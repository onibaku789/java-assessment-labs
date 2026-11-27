package study.locker.soap.bootstrap;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import study.locker.soap.wire.ObjectFactory;

import java.io.IOException;

@Configuration(proxyBeanMethods = false)
class SoapConfiguration {
    @Bean
    Jaxb2Marshaller lockerXml() {
        var marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath(ObjectFactory.class.getPackageName());
        return marshaller;
    }

    @Bean
    FilterRegistrationBean<HttpFilter> wsdlDiscovery() {
        var registration = new FilterRegistrationBean<HttpFilter>();
        registration.addUrlPatterns("/soap/lockers");
        registration.setFilter(new HttpFilter() {
            @Override
            protected void doFilter(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
                if ("GET".equals(request.getMethod()) && request.getParameterMap().containsKey("wsdl")) {
                    request.getRequestDispatcher("/soap/locker.wsdl").forward(request, response);
                    return;
                }
                chain.doFilter(request, response);
            }
        });
        return registration;
    }
}
