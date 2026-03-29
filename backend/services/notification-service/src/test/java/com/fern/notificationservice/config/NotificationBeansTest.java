package com.fern.notificationservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class NotificationBeansTest {
    private final NotificationBeans notificationBeans = new NotificationBeans();

    @Test
    void shouldTuneDatasourcePoolDefaults() throws Exception {
        setField(notificationBeans, "maxPoolSize", 8);
        setField(notificationBeans, "minIdle", 2);

        HikariDataSource dataSource = new HikariDataSource();
        DataSource tuned = invokeTunePool(notificationBeans, dataSource);

        assertThat(tuned).isSameAs(dataSource);
        assertThat(dataSource.getMaximumPoolSize()).isEqualTo(8);
        assertThat(dataSource.getMinimumIdle()).isEqualTo(2);
    }

    private DataSource invokeTunePool(NotificationBeans beans, DataSource dataSource) throws Exception {
        Method method = NotificationBeans.class.getDeclaredMethod("tunePool", DataSource.class);
        method.setAccessible(true);
        return (DataSource) method.invoke(beans, dataSource);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
