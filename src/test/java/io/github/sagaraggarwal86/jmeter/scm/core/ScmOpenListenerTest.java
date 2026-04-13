package io.github.sagaraggarwal86.jmeter.scm.core;

import org.apache.jmeter.gui.action.ActionNames;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ScmOpenListenerTest {

    @Test
    void getActionNamesContainsOpen() {
        ScmOpenListener listener = new ScmOpenListener();
        Set<String> names = listener.getActionNames();
        assertTrue(names.contains(ActionNames.OPEN));
    }

    @Test
    void getActionNamesContainsClose() {
        ScmOpenListener listener = new ScmOpenListener();
        Set<String> names = listener.getActionNames();
        assertTrue(names.contains(ActionNames.CLOSE));
    }

    @Test
    void getActionNamesContainsSubTreeLoaded() {
        ScmOpenListener listener = new ScmOpenListener();
        Set<String> names = listener.getActionNames();
        assertTrue(names.contains(ActionNames.SUB_TREE_LOADED));
    }

    @Test
    void getActionNamesReturnsThreeElements() {
        ScmOpenListener listener = new ScmOpenListener();
        assertEquals(3, listener.getActionNames().size());
    }

    @Test
    void defaultConstructorSucceeds() {
        assertDoesNotThrow(ScmOpenListener::new);
    }
}
