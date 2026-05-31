package Parking.model;

import Parking.enums.UserRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;
import jakarta.validation.constraints.*;


@Entity
@Getter
@Setter
@Table(name = "users")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "user_full_name", columnDefinition = "nvarchar(255)", nullable = false)
    @NotEmpty(message = "Full name is required")
    private String userFullName;

  
    @Column(name = "user_email" , columnDefinition = "NVARCHAR(255)", unique = true)
    @Email
    @NotEmpty(message = "Email cannot be empty!")
    private String userEmail;

    @Column(name = "user_password", nullable = false)
    private String userPassword;

 @Column(name = "user_phone", unique = true)
    @Pattern(regexp = "(03|05|07|08|09|012|016|018|019)[0-9]{8}$", message = "Phone invalid!")
    @NotEmpty(message = "Phone cannot be empty!" )
    private String userPhone;
    @Column(name = "user_address", columnDefinition = "nvarchar(255)", nullable = false)
    @NotEmpty(message = "Address cannot be empty!")
    private String userAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole userRole = UserRole.USER;
    @Column
    private boolean deleted = false;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + userRole.name()));
    }

    @Override
    public String getPassword() { 
        return userPassword;
    }

    @Override
    public String getUsername() { // spring security tự hiểu dùng email tài khoảng
        return userEmail;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() { // hàm này dùng để biết là tài khoảng bị khoá chưa
        return !deleted;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return !deleted;
    }
}