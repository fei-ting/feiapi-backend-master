package com.feiting.feiapi.bootstrap;

import com.feiting.feiapi.config.AdminBootstrapProperties;
import com.feiting.feiapi.service.AdminBootstrapService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 管理员一次性初始化任务入口。
 *
 * <p>该组件只在显式启用初始化开关时执行，完成后主动结束当前进程，避免作为常驻服务运行。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "feiapi.admin-bootstrap", name = "enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationListener<ApplicationReadyEvent> {

    /**
     * 防止应用就绪事件被重复处理。
     */
    private final AtomicBoolean executed = new AtomicBoolean(false);

    /**
     * 管理员初始化服务。
     */
    private final AdminBootstrapService adminBootstrapService;

    /**
     * 管理员初始化配置。
     */
    private final AdminBootstrapProperties properties;

    /**
     * 创建管理员初始化任务入口。
     *
     * @param adminBootstrapService 管理员初始化服务
     * @param properties            管理员初始化配置
     */
    public AdminBootstrapRunner(AdminBootstrapService adminBootstrapService,
                                AdminBootstrapProperties properties) {
        this.adminBootstrapService = adminBootstrapService;
        this.properties = properties;
    }

    /**
     * 在应用完成启动后执行一次管理员初始化，并以任务结果结束进程。
     *
     * @param event 应用就绪事件
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!executed.compareAndSet(false, true)) {
            return;
        }

        int exitCode = 0;
        try {
            boolean created = adminBootstrapService.initialize(
                    properties.getAccount(),
                    properties.getInitialPassword(),
                    properties.getDisplayName());
            log.info("管理员一次性初始化任务执行成功，created={}", created);
        } catch (RuntimeException exception) {
            exitCode = 1;
            log.error("管理员一次性初始化任务执行失败", exception);
        }
        terminateProcess(event.getApplicationContext(), exitCode);
    }

    /**
     * 关闭 Spring 上下文并结束一次性任务进程。
     *
     * @param context  Spring 应用上下文
     * @param exitCode 进程退出码
     */
    private void terminateProcess(ConfigurableApplicationContext context, int exitCode) {
        int applicationExitCode = SpringApplication.exit(context, () -> exitCode);
        System.exit(applicationExitCode);
    }
}
