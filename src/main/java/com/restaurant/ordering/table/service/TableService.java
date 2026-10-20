package com.restaurant.ordering.table.service;
import com.restaurant.ordering.common.config.AppProperties;
import com.restaurant.ordering.common.exception.ApiException;
import com.restaurant.ordering.table.dto.TableDtos;
import com.restaurant.ordering.table.entity.RestaurantTable;
import com.restaurant.ordering.table.qr.TableTokenService;
import com.restaurant.ordering.table.repository.RestaurantTableRepository;
import com.google.zxing.*;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import java.io.*;
import java.util.List;

@Service @RequiredArgsConstructor
public class TableService {
    private final RestaurantTableRepository tables;
    private final TableTokenService tokens;
    private final AppProperties properties;
    @Transactional(readOnly=true)
    public List<TableDtos.Response> list() { return tables.findAllByOrderByNumberAsc().stream().map(TableDtos.Response::from).toList(); }
    @Transactional
    public TableDtos.Response create(int number) { return TableDtos.Response.from(tables.saveAndFlush(new RestaurantTable(number))); }
    @Transactional
    public TableDtos.Response update(Long id, boolean active) { var t = required(id); t.setActive(active); tables.flush(); return TableDtos.Response.from(t); }
    @Transactional
    public TableDtos.Qr regenerate(Long id) { var t = required(id); t.regenerateToken(); tables.flush(); return qr(t); }
    @Transactional(readOnly=true)
    public TableDtos.Qr qr(Long id) {
        return qr(tables.findById(id).orElseThrow(() -> ApiException.notFound("TABLE_NOT_FOUND", "Table does not exist")));
    }
    @Transactional
    public TableDtos.Response resolve(int number, String token) { return TableDtos.Response.from(validateForOrder(number, token)); }
    // The read lock remains held by the enclosing order transaction, serializing QR revocation/deactivation.
    @Transactional
    public RestaurantTable validateForOrder(int number, String token) {
        var t = tables.lockByNumber(number).orElseThrow(() -> ApiException.notFound("TABLE_NOT_FOUND", "Table does not exist"));
        tokens.validate(t, token);
        if (!t.isActive()) throw ApiException.conflict("TABLE_INACTIVE", "Table is inactive");
        return t;
    }
    public byte[] png(TableDtos.Qr qr) {
        try {
            var matrix = new MultiFormatWriter().encode(qr.url(), BarcodeFormat.QR_CODE, 360, 360);
            var out = new ByteArrayOutputStream(); MatrixToImageWriter.writeToStream(matrix, "PNG", out); return out.toByteArray();
        } catch (WriterException | IOException ex) { throw new IllegalStateException("QR generation failed", ex); }
    }
    private TableDtos.Qr qr(RestaurantTable t) {
        var token = tokens.issue(t);
        var url = UriComponentsBuilder.fromUri(properties.guestMenuUrl()).queryParam("table", t.getNumber()).queryParam("token", token).build().encode().toUriString();
        return new TableDtos.Qr(url, token, t.getNumber(), t.getQrTokenVersion());
    }
    private RestaurantTable required(Long id) { return tables.lockById(id).orElseThrow(() -> ApiException.notFound("TABLE_NOT_FOUND", "Table does not exist")); }
}
