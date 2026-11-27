package study.locker.soap.bootstrap;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.jaxws.EndpointImpl;
import org.apache.cxf.transport.servlet.CXFNonSpringServlet;
import org.springframework.boot.*;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.*;

import study.locker.edge.application.LockerOperations;
import study.locker.soap.adapter.soap.LockerSoap;

@Configuration(proxyBeanMethods = false)
class SoapConfiguration {
    @Bean
    Bus lockerBus() {
        return BusFactory.newInstance().createBus();
    }

    @Bean
    ServletRegistrationBean<CXFNonSpringServlet> soapServlet(Bus bus) {
        var servlet = new CXFNonSpringServlet(null, bus);
        return new ServletRegistrationBean<>(servlet, "/soap/*");
    }

    @Bean(destroyMethod = "stop")
    EndpointImpl lockerEndpoint(Bus bus, LockerOperations gateway) {
        var endpoint = new EndpointImpl(bus, new LockerSoap(gateway));
        endpoint.setWsdlLocation("classpath:wsdl/locker.wsdl");
        endpoint.publish("/lockers");
        return endpoint;
    }
}
