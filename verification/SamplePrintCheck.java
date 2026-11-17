import com.eteks.sweethome3d.io.*;
import com.eteks.sweethome3d.model.*;
import com.eteks.sweethome3d.swing.*;
import com.eteks.sweethome3d.viewcontroller.*;
import java.awt.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

/** Verifies the supplied sample using the application's actual PDF printer. */
public class SamplePrintCheck {
  public static void main(String[] args) throws Exception {
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    SwingUtilities.invokeAndWait(() -> {
      try {
        DefaultUserPreferences preferences = new DefaultUserPreferences();
        preferences.setLanguage("en");
        HomeFileRecorder recorder = new HomeFileRecorder();
        Home home = recorder.readHome("samples/MultiLevelHome.sh3d");
        Level selected = home.getSelectedLevel();
        java.util.List<HomePieceOfFurniture.SortableProperty> columns = new ArrayList<>(home.getFurnitureVisibleProperties());
        if (!columns.contains(HomePieceOfFurniture.SortableProperty.VOLUME)) {
          columns.add(columns.indexOf(HomePieceOfFurniture.SortableProperty.HEIGHT) + 1, HomePieceOfFurniture.SortableProperty.VOLUME);
          home.setFurnitureVisibleProperties(columns);
        }
        HomeController controller = new HomeController(home, preferences, new SwingViewFactory(), new FileContentManager(preferences));
        controller.getView();
        FurnitureTable table = (FurnitureTable)controller.getFurnitureController().getView();
        JFrame printFrame = new JFrame("Sample print verification");
        printFrame.add(new JScrollPane(table));
        printFrame.setSize(1000, 400);
        printFrame.setVisible(true);
        printFrame.validate();
        java.util.List<HomePieceOfFurniture.SortableProperty> before = new ArrayList<>(home.getFurnitureVisibleProperties());
        HomePrintableComponent printed = new HomePrintableComponent(home, controller, new Font("SansSerif", Font.PLAIN, 12));
        int count = printed.getPageCount();
        if (count != 4) throw new AssertionError("Expected 4 sample print pages, got " + count);
        try (OutputStream output = new FileOutputStream("submissions/task4-all-levels.pdf")) {
          new HomePDFPrinter(home, preferences, controller, new Font("SansSerif", Font.PLAIN, 12)).write(output);
        }
        if (home.getSelectedLevel() != selected) throw new AssertionError("Sample selected level changed");
        if (!before.equals(home.getFurnitureVisibleProperties())) throw new AssertionError("Sample screen columns changed");
        // Make a disposable edited sample for visual review. Never overwrite the supplied file.
        for (HomePieceOfFurniture piece : home.getFurniture()) {
          piece.setDescription("Mock internship: edited description saved on furniture.");
        }
        recorder.writeHome(home, "target/EditedMultiLevelHome.sh3d");
        printFrame.dispose();
        System.out.println("Sample PDF: PASS (4 pages; selected level and screen columns unchanged)");
      } catch (Exception ex) { throw new RuntimeException(ex); }
    });
    System.exit(0);
  }
}
