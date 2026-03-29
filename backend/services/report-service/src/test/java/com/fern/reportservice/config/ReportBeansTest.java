package com.fern.reportservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class ReportBeansTest {
    private final ReportBeans reportBeans = new ReportBeans();

    @Test
    void shouldTuneDatasourcePoolDefaults() throws Exception {
        setField(reportBeans, "maxPoolSize", 9);
        setField(reportBeans, "minIdle", 3);

        HikariDataSource dataSource = new HikariDataSource();
        DataSource tuned = invokeTunePool(reportBeans, dataSource);

        assertThat(tuned).isSameAs(dataSource);
        assertThat(dataSource.getMaximumPoolSize()).isEqualTo(9);
        assertThat(dataSource.getMinimumIdle()).isEqualTo(3);
    }

    private DataSource invokeTunePool(ReportBeans beans, DataSource dataSource) throws Exception {
        Method method = ReportBeans.class.getDeclaredMethod("tunePool", DataSource.class);
        method.setAccessible(true);
        return (DataSource) method.invoke(beans, dataSource);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
