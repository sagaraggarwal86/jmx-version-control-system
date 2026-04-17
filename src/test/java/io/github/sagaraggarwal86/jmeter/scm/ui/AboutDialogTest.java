package io.github.sagaraggarwal86.jmeter.scm.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import javax.swing.*;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class AboutDialogTest {

    private MockedStatic<JOptionPane> optionPaneMock;

    @BeforeEach
    void setUp() {
        optionPaneMock = mockStatic(JOptionPane.class);
    }

    @AfterEach
    void tearDown() {
        optionPaneMock.close();
    }

    @Test
    void showDialogCallsJOptionPane() {
        AboutDialog.showDialog(null);

        optionPaneMock.verify(() ->
                JOptionPane.showMessageDialog(eq(null), any(), eq("About — JVCS"), eq(JOptionPane.PLAIN_MESSAGE)));
    }

    @Test
    void showDialogDoesNotThrowWithNullParent() {
        assertDoesNotThrow(() -> AboutDialog.showDialog(null));
    }

    @Test
    void showDialogPassesPanelContent() {
        AboutDialog.showDialog(null);

        optionPaneMock.verify(() ->
                JOptionPane.showMessageDialog(eq(null), argThat(arg -> {
                    if (arg instanceof JPanel panel) {
                        // Verify the panel has at least title and description labels
                        return panel.getComponentCount() > 0;
                    }
                    return false;
                }), anyString(), anyInt()));
    }
}
