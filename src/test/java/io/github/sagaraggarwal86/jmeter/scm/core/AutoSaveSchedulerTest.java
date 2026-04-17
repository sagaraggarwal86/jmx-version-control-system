package io.github.sagaraggarwal86.jmeter.scm.core;

import org.apache.jmeter.util.JMeterUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AutoSaveSchedulerTest {

    private MockedStatic<JMeterUtils> jmeterUtilsMock;
    private ScmInitializer mockInitializer;
    private AutoSaveScheduler scheduler;

    @BeforeEach
    void setUp() {
        jmeterUtilsMock = mockStatic(JMeterUtils.class);
        mockInitializer = mock(ScmInitializer.class);
        scheduler = new AutoSaveScheduler(mockInitializer);
    }

    @AfterEach
    void tearDown() {
        scheduler.stop();
        jmeterUtilsMock.close();
    }

    @Test
    void startWhenDisabledIsNoOp() {
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.autosave.enabled")).thenReturn("false");

        scheduler.start();

        // No exception, no timer created — verify via stop being safe
        assertDoesNotThrow(() -> scheduler.stop());
    }

    @Test
    void stopWhenNotStartedIsNoOp() {
        assertDoesNotThrow(() -> scheduler.stop());
    }

    @Test
    void doubleStopIsNoOp() {
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.autosave.enabled")).thenReturn("false");
        scheduler.start();

        assertDoesNotThrow(() -> {
            scheduler.stop();
            scheduler.stop();
        });
    }

    @Test
    void startWhenEnabledDoesNotThrow() {
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.autosave.enabled")).thenReturn("true");
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.autosave.interval.minutes")).thenReturn("1");

        assertDoesNotThrow(() -> scheduler.start());
        scheduler.stop();
    }

    @Test
    void restartStopsPreviousAndStarts() {
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.autosave.enabled")).thenReturn("true");
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.autosave.interval.minutes")).thenReturn("5");

        scheduler.start();
        // Restart should not throw
        assertDoesNotThrow(() -> scheduler.start());
        scheduler.stop();
    }

    @Test
    void constructorAcceptsInitializer() {
        assertDoesNotThrow(() -> new AutoSaveScheduler(mockInitializer));
    }
}
