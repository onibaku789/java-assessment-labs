package study.locker.common;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

public final class RpcClientFixture {
    private RpcClientFixture() {}

    public static AnnotationConfigApplicationContext open(RpcTarget target, SimpleMeterRegistry metrics,
                                                          Map<String, Object> properties) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        context.registerBean("rpcTarget", RpcTarget.class, () -> target);
        context.registerBean(SimpleMeterRegistry.class, () -> metrics);
        context.register(RpcClientConfiguration.class);
        context.refresh();
        return context;
    }
}
