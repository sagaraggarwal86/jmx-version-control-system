package io.github.sagaraggarwal86.jmeter.scm.ui;

import org.apache.jmeter.gui.GuiPackage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mockStatic;

class ToastTest {

    private MockedStatic<GuiPackage> guiPackageMock;

    @BeforeEach
    void setUp() {
        guiPackageMock = mockStatic(GuiPackage.class);
        guiPackageMock.when(GuiPackage::getInstance).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        guiPackageMock.close();
    }

    @Test
    void showWithNullParentDoesNotThrow() {
        assertDoesNotThrow(() -> Toast.show(null, "Test message"));
    }

    @Test
    void showWithEmptyMessageDoesNotThrow() {
        assertDoesNotThrow(() -> Toast.show(null, ""));
    }

    @Test
    void showStaticMethodDoesNotThrow() {
        // When GuiPackage returns null, getParentWindow returns null — should handle gracefully
        assertDoesNotThrow(() -> Toast.show("Test notification"));
    }
}
