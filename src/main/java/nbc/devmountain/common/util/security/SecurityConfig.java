package nbc.devmountain.common.util.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;
import nbc.devmountain.common.util.oauth2.CustomOAuth2UserService;
import nbc.devmountain.domain.user.model.User;
import nbc.devmountain.domain.user.repository.UserRepository;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http, CustomOAuth2UserService customOAuth2UserService) throws
		Exception {
		http
			// .cors().and()
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))
			.csrf().disable() //csrf 비활성화
			.sessionManagement(session ->
				session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)) // 세션 기반 인증 사용
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/users/signup", "/users/login", "/login",
					"/ws/**", "/chatrooms/**", "/chatrooms", "/info", "/error", "/topic/**", "/app/**", "/actuator",
					"/actuator/**", "/lectures/embedding", "/lectures/inflearn","/batches/result/inflearn","/batches/result/embedding")
				.permitAll() // 인증이 필요없는 부분 추가 예정
				.anyRequest()
				.authenticated()
			)
			// OAuth2 설정
			.oauth2Login(oauth -> oauth
				// .loginPage("/login")
				.userInfoEndpoint(userInfo -> userInfo
					.userService(customOAuth2UserService)) // 사용자 정보 처리
				// .defaultSuccessUrl("/") // 로그인 성공 후 이동할 페이지
				.successHandler((request, response, authentication) -> {
					response.sendRedirect("http://localhost:5173/home");
				})
			)
			// 일반 로그인은 UserController에서 처리하므로 비활성화
			.formLogin(form -> form.disable())
			// 로그아웃
			.logout(logout -> logout
				.logoutUrl("/logout")
				.logoutSuccessHandler((request, response, authentication) -> {
					response.setStatus(HttpServletResponse.SC_OK);
				})
				.invalidateHttpSession(true)
				.deleteCookies("JSESSIONID")
			);

		return http.build();
	}

	// cors 커스텀
	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(
			Arrays.asList("http://localhost:5173", "http://frontend:5173", "http://13.209.155.21:5173"));
		configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(Arrays.asList("*"));
		configuration.setAllowCredentials(true);
		configuration.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	// 인증 매니저 Bean 등록
	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}

	// 사용자 인증 정보 불러오기
	@Bean
	public UserDetailsService userDetailsService(UserRepository userRepository) {
		return username -> {
			// email 로 조회
			User user = userRepository.findByEmail(username)
				.orElseThrow(() -> new UsernameNotFoundException("User not found"));
			return new CustomUserPrincipal(user);
		};
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
