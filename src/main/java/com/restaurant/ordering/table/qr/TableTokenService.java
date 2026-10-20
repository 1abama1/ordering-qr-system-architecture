package com.restaurant.ordering.table.qr;
import com.restaurant.ordering.common.config.AppProperties;
import com.restaurant.ordering.common.exception.ApiException;
import com.restaurant.ordering.table.entity.RestaurantTable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

@Service
public class TableTokenService {
    private final byte[] secret;
    public TableTokenService(AppProperties properties) { secret = properties.qrSecret().getBytes(StandardCharsets.UTF_8); }
    public String issue(RestaurantTable table) {
        var data = Base64.getUrlEncoder().withoutPadding().encodeToString(
            ("v1:" + table.getId() + ":" + table.getNumber() + ":" + table.getQrTokenVersion()).getBytes(StandardCharsets.UTF_8));
        return data + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(data));
    }
    public void validate(RestaurantTable table, String token) {
        // Compare the complete deterministic token in constant time, including the version and table binding.
        if (token == null || token.length() > 256 || !MessageDigest.isEqual(issue(table).getBytes(StandardCharsets.US_ASCII),
                token.getBytes(StandardCharsets.UTF_8)))
            throw new ApiException(HttpStatus.FORBIDDEN, "INVALID_TABLE_TOKEN", "Invalid or revoked table token");
    }
    private byte[] sign(String data) {
        try {
            var mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException ex) { throw new IllegalStateException("HMAC unavailable", ex); }
    }
}
