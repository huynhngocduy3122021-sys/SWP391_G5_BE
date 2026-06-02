package Parking.Service;

import org.springframework.beans.factory.annotation.Autowired;  
import Parking.Repository.UserRepository;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.util.Date;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import Parking.Model.User;
import io.jsonwebtoken.Claims;
import java.util.function.Function;
import org.springframework.stereotype.Service;
@Service
public class TokenService {

    private final String SECRET_KET = "Y2JUQaJ6bScNaYzejPyyHRXYme4gTHKjY2JUQaJ6bScNaYzejPyyHRXYme4";

    @Autowired
    private UserRepository userRepository;

    public SecretKey getSecretKey() {
        byte[] keyBytes =  Decoders.BASE64.decode(SECRET_KET);
        return Keys.hmacShaKeyFor(keyBytes);
    }

public String generateToken(User user) {
        return Jwts.builder()
                .subject(user.getUserId() + "") // Sử dụng userId làm subject
                .issuedAt(new Date(System.currentTimeMillis())) // Thời điểm tạo token
                .expiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10)) // Token có hiệu lực trong 10 giờ
                .signWith(getSecretKey()) // Ký token bằng secret key
                .compact();


    }
    //verify token
    public User extractToken(String token) {
        String value = getClaimFromToken(token, Claims::getSubject);
        Long id = Long.parseLong(value);
        return userRepository.findById(id).orElse(null); // Tìm người dùng theo id lấy được từ token
    }

    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    } // lấy ra thông tin của token
    

     public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        Claims claims = getClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }
}
