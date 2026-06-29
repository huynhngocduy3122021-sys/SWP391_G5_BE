package Parking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.scheduling.annotation.EnableScheduling;

// Đánh dấu đây là class chạy chính (Entry Point) của ứng dụng Spring Boot
@SpringBootApplication
@EnableScheduling
public class Main {

    public static void main(String[] args) {
    //     // Tắt chế độ 'headless' của Java AWT để cho phép mở các thành phần giao diện máy tính (như Trình duyệt)
    //     System.setProperty("java.awt.headless", "false");
        
    //     // Tạo đối tượng khởi chạy ứng dụng Spring
    //     SpringApplication app = new SpringApplication(Main.class);
        
    //     // Đảm bảo Spring Boot không tự động chuyển về chế độ headless khi khởi động
    //     app.setHeadless(false);
        
    //     // Đăng ký bộ lắng nghe sự kiện: Khi ứng dụng khởi động thành công và sẵn sàng (ApplicationReadyEvent), 
    //     // nó sẽ tự động chạy hàm openBrowser để mở trang web localhost:8080
    //     app.addListeners((ApplicationListener<ApplicationReadyEvent>) event -> openBrowser("http://localhost:8080"));
        
    //     // Bắt đầu khởi chạy toàn bộ server Spring Boot
    //     app.run(args);
    // }

    // /**
    //  * Hàm dùng để tự động mở một đường dẫn URL trên trình duyệt mặc định của hệ điều hành.
    //  * @param url Đường dẫn trang web cần mở (VD: http://localhost:8080)
    //  */
    // private static void openBrowser(String url) {
    //     try {
    //         // Kiểm tra xem hệ thống có hỗ trợ tính năng Desktop của Java và hỗ trợ duyệt Web (Action.BROWSE) hay không
    //         if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
    //             // Nếu có, dùng thư viện tiêu chuẩn để mở URL
    //             Desktop.getDesktop().browse(new URI(url));
    //         } else {
    //             // Nếu không hỗ trợ Desktop API (VD: chạy trên một số môi trường đặc biệt), 
    //             // chúng ta sẽ kiểm tra hệ điều hành và chạy lệnh CMD tương ứng để ép buộc mở trình duyệt.
    //             String os = System.getProperty("os.name").toLowerCase();
    //             if (os.contains("win")) {
    //                 // Trên Windows: Dùng rundll32 để gọi trình duyệt mặc định mở link
    //                 Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
    //             } else if (os.contains("mac")) {
    //                 // Trên macOS: Chạy lệnh 'open' để mở link
    //                 Runtime.getRuntime().exec("open " + url);
    //             } else if (os.contains("nix") || os.contains("nux")) {
    //                 // Trên Linux/Unix: Chạy lệnh 'xdg-open' để mở link
    //                 Runtime.getRuntime().exec("xdg-open " + url);
    //             } else {
    //                 // Nếu hệ điều hành lạ không hỗ trợ, chỉ in đường dẫn ra màn hình console
    //                 System.out.println("Hãy truy cập trình duyệt tại: " + url);
    //             }
    //         }
    //     } catch (IOException | URISyntaxException e) {
    //         // Xử lý lỗi nếu việc tự động mở trình duyệt bị thất bại
    //         System.err.println("Không thể mở trình duyệt tự động: " + e.getMessage());
    //     }
     SpringApplication.run(Main.class, args);
    }

}