package io.github.sagaraggarwal86.jmeter.scm.core;

import org.apache.jmeter.gui.action.ActionNames;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.event.ActionEvent;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SaveCommandWrapperTest {

    private MockedStatic<ScmInitializer> initializerMock;
    private ScmInitializer mockInitializer;

    @BeforeEach
    void setUp() {
        mockInitializer = mock(ScmInitializer.class);
        initializerMock = mockStatic(ScmInitializer.class);
        initializerMock.when(ScmInitializer::getInstance).thenReturn(mockInitializer);
    }

    @AfterEach
    void tearDown() {
        initializerMock.close();
    }

    @Test
    void getActionNamesContainsSave() {
        SaveCommandWrapper wrapper = new SaveCommandWrapper();
        Set<String> names = wrapper.getActionNames();

        assertTrue(names.contains(ActionNames.SAVE));
    }

    @Test
    void getActionNamesReturnsSingleElement() {
        SaveCommandWrapper wrapper = new SaveCommandWrapper();
        assertEquals(1, wrapper.getActionNames().size());
    }

    @Test
    void doActionCallsEnsureInitializedWithContext() {
        SaveCommandWrapper wrapper = new SaveCommandWrapper();
        ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, ActionNames.SAVE);

        wrapper.doAction(event);

        verify(mockInitializer).ensureInitializedWithContext();
    }

    @Test
    void doActionHandlesExceptionGracefully() {
        doThrow(new RuntimeException("test failure"))
            .when(mockInitializer).ensureInitializedWithContext();

        SaveCommandWrapper wrapper = new SaveCommandWrapper();
        ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, ActionNames.SAVE);

        assertDoesNotThrow(() -> wrapper.doAction(event));
    }

    @Test
    void defaultConstructorSucceeds() {
        assertDoesNotThrow(SaveCommandWrapper::new);
    }
}
