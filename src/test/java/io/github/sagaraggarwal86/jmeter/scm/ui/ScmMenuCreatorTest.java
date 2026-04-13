package io.github.sagaraggarwal86.jmeter.scm.ui;

import io.github.sagaraggarwal86.jmeter.scm.core.ScmInitializer;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.plugin.MenuCreator;
import org.apache.jmeter.util.JMeterUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import javax.swing.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScmMenuCreatorTest {

    private MockedStatic<ScmInitializer> initializerMock;
    private MockedStatic<GuiPackage> guiPackageMock;
    private MockedStatic<JMeterUtils> jmeterUtilsMock;
    private ScmInitializer mockInitializer;

    @BeforeEach
    void setUp() {
        mockInitializer = mock(ScmInitializer.class);
        initializerMock = mockStatic(ScmInitializer.class);
        initializerMock.when(ScmInitializer::getInstance).thenReturn(mockInitializer);

        guiPackageMock = mockStatic(GuiPackage.class);
        guiPackageMock.when(GuiPackage::getInstance).thenReturn(null);

        jmeterUtilsMock = mockStatic(JMeterUtils.class);
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty(anyString())).thenReturn(null);
        jmeterUtilsMock.when(JMeterUtils::getJMeterVersion).thenReturn("5.6.3");
    }

    @AfterEach
    void tearDown() {
        jmeterUtilsMock.close();
        guiPackageMock.close();
        initializerMock.close();
    }

    @Test
    void getTopLevelMenusReturnsEmpty() {
        ScmMenuCreator creator = new ScmMenuCreator();
        JMenu[] menus = creator.getTopLevelMenus();
        assertEquals(0, menus.length);
    }

    @Test
    void getMenuItemsAtToolsReturnsItems() {
        ScmMenuCreator creator = new ScmMenuCreator();
        JMenuItem[] items = creator.getMenuItemsAtLocation(MenuCreator.MENU_LOCATION.TOOLS);
        assertNotNull(items);
        assertTrue(items.length > 0);
    }

    @Test
    void getMenuItemsAtNonToolsReturnsEmpty() {
        ScmMenuCreator creator = new ScmMenuCreator();
        JMenuItem[] items = creator.getMenuItemsAtLocation(MenuCreator.MENU_LOCATION.FILE);
        assertEquals(0, items.length);
    }

    @Test
    void localeChangedIsNoOp() {
        ScmMenuCreator creator = new ScmMenuCreator();
        assertDoesNotThrow(() -> creator.localeChanged());
    }

    @Test
    void localeChangedWithMenuReturnsFalse() {
        ScmMenuCreator creator = new ScmMenuCreator();
        assertFalse(creator.localeChanged(new JMenu()));
    }

    @Test
    void constructorDoesNotThrow() {
        assertDoesNotThrow(ScmMenuCreator::new);
    }
}
