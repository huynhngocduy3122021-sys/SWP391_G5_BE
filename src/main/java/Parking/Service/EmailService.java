package Parking.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendOtpEmail(String toEmail, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("giahuy2710005@gmail.com"); // Thay bằng email của bạn
        message.setTo(toEmail);
        message.setSubject("Mã OTP Đăng Ký Tài Khoản");
        message.setText("Chào bạn,\n\nMã OTP xác thực đăng ký tài khoản của bạn là: " + otp + "\n\nVui lòng không chia sẻ mã này cho bất kỳ ai.\n\nTrân trọng,\nĐội ngũ Smart Parking");

        try {
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Lỗi gửi email: " + e.getMessage());
            // In thực tế nên throw exception để frontend báo lỗi
            // throw new RuntimeException("Không thể gửi email OTP, vui lòng thử lại sau.");
        }
    }
}
