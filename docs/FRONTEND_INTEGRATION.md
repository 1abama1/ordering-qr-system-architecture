# React / Axios integration

Используйте публичный адрес Nginx: localhost:8080 локально, HTTPS в production. React dev origin localhost:5173 разрешён по умолчанию. Backend не хранит корзину; подтверждённые суммы и названия берите из Order DTO.

## QR и оформление

```js
import axios from 'axios';
const api = axios.create({ baseURL: import.meta.env.VITE_API_URL });
const params = new URLSearchParams(window.location.search);
const tableNumber = Number(params.get('table'));
const tableToken = params.get('token');
await api.get('/api/tables/resolve', { params: { tableNumber, tableToken } });
const { data: menu } = await api.get('/api/menu', { params: { includeUnavailable: true } });
// Уберите token из адресной строки после считывания, храните его в sessionStorage
// для текущей вкладки. Не отправляйте URL с token в analytics.
const pendingAttempt = {
  key: crypto.randomUUID(),
  body: { tableNumber, tableToken, items: [{ menuItemId: menu[0].id, quantity: 2 }] }
};
// Сохраните pendingAttempt в sessionStorage ДО запроса.
// При network error повторите тот же body и key.
// При изменении корзины после явной ошибки создайте новый key.
const response = await api.post('/api/orders', pendingAttempt.body, {
  headers: { 'Idempotency-Key': pendingAttempt.key }
});
console.log(response.data.id, response.data.totalAmount);
```

При 409 MENU_ITEM_UNAVAILABLE перечитайте меню, удалите/отметьте недоступную позицию в корзине и попросите гостя подтвердить обновлённый состав. При 403 INVALID_TABLE_TOKEN нужно повторно отсканировать актуальный QR. Не подменяйте tableNumber вручную и не передавайте price.

## Staff REST

```js
const { data: session } = await api.post('/api/staff/auth/login', { username, password });
let accessToken = session.accessToken; // держите в памяти, не в localStorage
const staff = axios.create({ baseURL: import.meta.env.VITE_API_URL });
staff.interceptors.request.use(config => {
  config.headers.Authorization = 'Bearer ' + accessToken;
  return config;
});
await staff.get('/api/staff/auth/me');
await staff.patch('/api/orders/42/status', { status: 'COOKING' });
await staff.patch('/api/orders/42/complete');
await staff.patch('/api/menu/1/availability', { isAvailable: false });
```

JWT bearer токен доступен JavaScript: XSS даёт доступ к нему. Храните в памяти, применяйте CSP и не вставляйте HTML из menu description. На 401 остановите защищённый WS и покажите login. Не повторяйте бесконечно 403/409. Refresh token/cookie-auth не предусмотрены.

## STOMP и authoritative snapshot

```js
import { Client } from '@stomp/stompjs';

let disposed = false, dirty = false, running = false;
async function loadActive() {
  const orders = new Map();
  for (let page = 0; ; page++) {
    const { data } = await staff.get('/api/orders/active', { params: { page, size: 200 } });
    for (const order of data) orders.set(order.id, order);
    if (data.length === 0) break;
  }
  return [...orders.values()];
}
async function invalidate() {
  dirty = true;
  if (running || disposed) return;
  running = true;
  try {
    do {
      dirty = false;
      const snapshot = await loadActive();
      if (!disposed) setOrders(snapshot); // React state setter
    } while (dirty && !disposed);
  } catch (error) {
    if (!disposed) setConnectionError(error); // отобразить stale/offline state
  } finally {
    running = false;
  }
}
const ws = new Client({
  brokerURL: import.meta.env.VITE_WS_URL, // ws://localhost:8080/ws
  reconnectDelay: 3000,
  heartbeatIncoming: 10000,
  heartbeatOutgoing: 10000,
  beforeConnect: () => { ws.connectHeaders = { Authorization: 'Bearer ' + accessToken }; },
  onConnect: () => {
    const receipt = crypto.randomUUID();
    ws.watchForReceipt(receipt, () => invalidate());
    ws.subscribe('/topic/kitchen/orders', () => invalidate(), { receipt });
  },
  onStompError: () => setConnectionError(new Error('STOMP authorization/connection error')),
  onWebSocketClose: () => setConnectionError(new Error('Offline; reconnecting'))
});
ws.activate();
const periodic = setInterval(invalidate, 15000);
const onVisible = () => { if (document.visibilityState === 'visible') invalidate(); };
document.addEventListener('visibilitychange', onVisible);
// React effect cleanup:
async function cleanup() {
  disposed = true;
  clearInterval(periodic);
  document.removeEventListener('visibilitychange', onVisible);
  await ws.deactivate();
}
```

Это пример для одного React effect; setOrders/setConnectionError предоставляются компонентом. При receipt timeout пересоздайте соединение (watchForReceipt callback не вызывается), например собственным 5-секундным таймером. При expiresAt завершите защищённое соединение и повторите login; после получения нового token активируйте снова. Не включайте STOMP debug, печатающий CONNECT headers.

Гостевой клиент использует тот же алгоритм без Authorization, destination /topic/menu/updates, а loader получает menu?includeUnavailable=true и categories. Во время unavailable устанавливайте disabled у блюда и удаляйте его из доступных действий. Server в любом случае повторно проверяет наличие. После MENU_CHANGED/CATEGORIES_CHANGED refetch нужен также для цен, category order и скрытых позиций.

Событие может прийти во время REST fetch: dirty-loop повторяет запрос. RECEIPT закрывает гонку между регистрацией подписки и первым snapshot. Periodic refetch устраняет устойчивый рассинхрон после потери публикации. Карточки KDS отображаются по order.id; COMPLETED отсутствуют в новом snapshot.
