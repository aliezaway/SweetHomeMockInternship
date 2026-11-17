import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.*;
import com.eteks.sweethome3d.swing.*;
import com.eteks.sweethome3d.viewcontroller.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import java.io.*;
import java.text.MessageFormat;
import java.util.*;
import javax.swing.*;
import javax.swing.table.TableColumnModel;

/** Additional behavioral checks, kept separate from the provided acceptance tests. */
public class InternshipRegressionChecks {
  private static final DefaultUserPreferences PREFS = new DefaultUserPreferences();
  private static int assertions;
  private static void check(boolean condition, String message) {
    assertions++;
    if (!condition) throw new AssertionError(message);
  }
  private static HomePieceOfFurniture piece(String name, float width) {
    return new HomePieceOfFurniture(new CatalogPieceOfFurniture(name, null, null, width, 3, 4, true, false));
  }
  public static void main(String[] args) throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try {
        descriptionAndVolume();
        planPagesAndFailures();
        tablePrintingAndFailures();
        printedHeadersFollowPlanPages();
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    });
    System.out.println("Additional regression checks: PASS (" + assertions + " assertions)");
  }
  private static void descriptionAndVolume() throws Exception {
    CatalogPieceOfFurniture catalog = new CatalogPieceOfFurniture("Catalog", null, null, 2, 3, 4, true, false);
    check(catalog.getVolume() == 24, "Catalog volume");
    Home home = new Home();
    HomePieceOfFurniture small = piece("Small", 2), large = piece("Large", 5);
    home.addPieceOfFurniture(small); home.addPieceOfFurniture(large);
    home.setSelectedItems(Collections.<Selectable>singletonList(small));
    HomeFurnitureController editor = new HomeFurnitureController(home, PREFS, null, null);
    editor.setDescription("Edited description saved on furniture");
    editor.modifyFurniture();
    check("Edited description saved on furniture".equals(small.getDescription()), "Description saved");
    home.setFurnitureSortedProperty(HomePieceOfFurniture.SortableProperty.VOLUME);
    FurnitureTable table = new FurnitureTable(home, PREFS);
    check(table.getValueAt(0, 0) == small, "Volume ascending");
    home.setFurnitureDescendingSorted(true);
    check(table.getValueAt(0, 0) == large, "Volume descending");
    small.setWidth(10);
    check(small.getVolume() == 120 && table.getValueAt(0, 0) == small, "Resize updates volume and sort");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    new ObjectOutputStream(bytes).writeObject(home);
    Home loaded = (Home)new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).readObject();
    check(loaded.getFurnitureSortedProperty() == HomePieceOfFurniture.SortableProperty.VOLUME, "Volume sort saved");
    check(loaded.getFurnitureVisibleProperties().contains(HomePieceOfFurniture.SortableProperty.VOLUME), "Volume column saved");
    check(loaded.getFurniture().get(0).getDescription().equals(small.getDescription()), "Description survives save/reopen");
  }
  private static Home threeLevels() {
    Home home = new Home();
    for (int i = 0; i < 3; i++) {
      Level level = new Level("Level " + i, i * 250, 10, 240); home.addLevel(level);
      Wall wall = new Wall(i * 60, 0, 100 + i * 100, 80 + i * 50, 10, 240);
      wall.setLevel(level); home.addWall(wall);
      HomePieceOfFurniture furniture = piece("Furniture " + i, 20 + i * 10);
      home.addPieceOfFurniture(furniture); furniture.setLevel(level);
    }
    home.setSelectedLevel(home.getLevels().get(2));
    return home;
  }
  private static PageFormat page() {
    Paper paper = new Paper(); paper.setSize(612, 792); paper.setImageableArea(36, 36, 540, 720);
    PageFormat page = new PageFormat(); page.setPaper(paper); return page;
  }
  private static void planPagesAndFailures() {
    Home home = threeLevels(); Level selected = home.getSelectedLevel();
    final java.util.List<Level> visited = new ArrayList<Level>();
    PlanComponent plan = new PlanComponent(home, PREFS, null);
    home.addPropertyChangeListener(Home.Property.SELECTED_LEVEL, event -> visited.add(home.getSelectedLevel()));
    Graphics2D g = new BufferedImage(800, 1000, BufferedImage.TYPE_INT_RGB).createGraphics();
    try {
      for (int i = 0; i < 3; i++) {
        visited.clear();
        check(plan.print(g, page(), i) == Printable.PAGE_EXISTS, "Level page " + i);
        check(home.getSelectedLevel() == selected, "Selection restored after page " + i);
        if (i != 2) check(visited.get(0) == home.getLevels().get(i), "Home level order " + i);
      }
      check(plan.print(g, page(), 3) == Printable.NO_SUCH_PAGE, "No fourth level");
      check(plan.print(g, page(), -1) == Printable.NO_SUCH_PAGE, "Reject negative page");
      home.getLevels().get(1).setViewable(false);
      check(plan.print(g, page(), 1) == Printable.PAGE_EXISTS, "Hidden level skipped");
      check(plan.print(g, page(), 2) == Printable.NO_SUCH_PAGE, "Hidden level removes page");
      PlanComponent broken = new PlanComponent(home, PREFS, null) {
        @Override protected java.util.List<Selectable> getPaintedItems() {
          throw new IllegalStateException("Injected render failure");
        }
      };
      try { broken.print(g, page(), 0); throw new AssertionError("Expected failure"); }
      catch (IllegalStateException expected) {
        check(home.getSelectedLevel() == selected, "Selection restored on render failure");
      }
      Home emptyLevelHome = new Home(); emptyLevelHome.addLevel(new Level("Empty", 0, 10, 240));
      check(new PlanComponent(emptyLevelHome, PREFS, null).print(g, page(), 0) == Printable.PAGE_EXISTS,
          "Empty viewable level still has a page");
      Home noLevels = new Home(); noLevels.addWall(new Wall(0, 0, 100, 100, 10, 240));
      PlanComponent single = new PlanComponent(noLevels, PREFS, null);
      check(single.print(g, page(), 0) == Printable.PAGE_EXISTS, "Homes without levels still print");
      check(single.print(g, page(), 1) == Printable.NO_SUCH_PAGE, "Single plan terminates");
    } finally { g.dispose(); }
  }
  private static void tablePrintingAndFailures() throws Exception {
    Home home = threeLevels(); home.getLevels().get(1).setViewable(false);
    CapturingTable table = new CapturingTable(home);
    FurnitureTable.FurnitureFilter screenFilter = (h, p) -> p.getLevel() == home.getSelectedLevel();
    table.setFurnitureFilter(screenFilter);
    TableColumnModel screenColumns = table.getColumnModel();
    java.util.List<HomePieceOfFurniture.SortableProperty> visible = new ArrayList<>(home.getFurnitureVisibleProperties());
    Graphics2D g = new BufferedImage(800, 1000, BufferedImage.TYPE_INT_RGB).createGraphics();
    try {
      check(table.getRowCount() == 1, "Screen filter active");
      check(table.print(g, page(), 0) == Printable.PAGE_EXISTS, "Furniture page exists");
      check(table.printedRows == 2, "Print every viewable level, exclude hidden level");
      check(table.printedLevelColumns == 1, "Printed table has one Level column");
      check(table.getColumnModel() == screenColumns && visible.equals(home.getFurnitureVisibleProperties()), "Screen columns unchanged");
      check(table.getFurnitureFilter() == screenFilter && table.getRowCount() == 1, "Screen filter restored");
      table.fail = true;
      try { table.print(g, page(), 0); throw new AssertionError("Expected PrinterException"); }
      catch (PrinterException expected) {
        check(table.getColumnModel() == screenColumns, "Columns restored on print failure");
        check(table.getFurnitureFilter() == screenFilter, "Filter restored on print failure");
      }
      table.fail = false;
      java.util.List<HomePieceOfFurniture.SortableProperty> withLevel = new ArrayList<>(visible);
      withLevel.add(HomePieceOfFurniture.SortableProperty.LEVEL); home.setFurnitureVisibleProperties(withLevel);
      table.print(g, page(), 0);
      check(table.printedLevelColumns == 1, "No duplicate Level when already visible");
      check(home.getFurnitureVisibleProperties().equals(withLevel), "Visible Level retained");
    } finally { g.dispose(); }
  }
  private static void printedHeadersFollowPlanPages() throws Exception {
    Home home = threeLevels();
    home.getLevels().get(1).setViewable(false);
    // The visible floors have markedly different dimensions.
    Wall topWall = new ArrayList<Wall>(home.getWalls()).get(2);
    topWall.setXEnd(5000);
    topWall.setYEnd(3000);
    Level selected = home.getSelectedLevel();
    home.setPrint(new HomePrint(HomePrint.PaperOrientation.PORTRAIT,
        612, 792, 36, 36, 36, 36, true, true, false, 0.01f,
        "Level: $level", "Scale: $planScale"));
    final float[] renderedScale = new float[1];
    final Level[] renderedLevel = new Level[1];
    final boolean[] insidePlanPrint = new boolean[1];
    final boolean[] failHeaderScale = new boolean[1];
    SwingViewFactory views = new SwingViewFactory() {
      @Override public View createFurnitureView(Home h, UserPreferences p, FurnitureController c) {
        return new FurnitureTable(h, p) {
          @Override public Printable getPrintable(JTable.PrintMode mode, MessageFormat header, MessageFormat footer) {
            // Keep one furniture page without requiring a visible Swing window.
            return (g, format, index) -> index == 0 ? Printable.PAGE_EXISTS : Printable.NO_SUCH_PAGE;
          }
        };
      }
      @Override public PlanView createPlanView(Home h, UserPreferences p, PlanController c) {
        return new PlanComponent(h, p, null) {
          @Override public float getPrintPreferredScale(Graphics g, PageFormat format) {
            if (failHeaderScale[0]) throw new IllegalStateException("Injected header scale failure");
            float scale = super.getPrintPreferredScale(g, format);
            if (insidePlanPrint[0]) {
              // This call is made by the actual renderer after it selects the
              // requested floor, not by the header's preliminary calculation.
              renderedScale[0] = scale;
              renderedLevel[0] = h.getSelectedLevel();
            }
            return scale;
          }
          @Override public int print(Graphics g, PageFormat format, int index) {
            insidePlanPrint[0] = true;
            try { return super.print(g, format, index); }
            finally { insidePlanPrint[0] = false; }
          }
        };
      }
      @Override public View createView3D(Home h, UserPreferences p, HomeController3D c) { return null; }
    };
    HomeController controller = new HomeController(home, PREFS, views, new FileContentManager(PREFS));
    HomePrintableComponent printable = new HomePrintableComponent(home, controller, new Font("SansSerif", Font.PLAIN, 12));
    check(printable.getPageCount() == 3, "Header test has furniture plus two viewable plans");
    // Request the last page first, as a print preview can do.
    for (int index : new int[] {2, 1}) {
      renderedLevel[0] = null;
      renderedScale[0] = Float.NaN;
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      com.lowagie.text.Document document = new com.lowagie.text.Document(new com.lowagie.text.Rectangle(612, 792));
      com.lowagie.text.pdf.PdfWriter writer = com.lowagie.text.pdf.PdfWriter.getInstance(document, output);
      document.open();
      Graphics2D graphics = writer.getDirectContent().createGraphics(612, 792);
      try {
        check(printable.print(graphics, page(), index) == Printable.PAGE_EXISTS, "Header plan page exists");
      } finally { graphics.dispose(); document.close(); }
      com.lowagie.text.pdf.PdfReader reader = new com.lowagie.text.pdf.PdfReader(output.toByteArray());
      String text;
      try { text = new com.lowagie.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(1); }
      finally { reader.close(); }
      String expectedLevel = index == 1 ? "Level 0" : "Level 2";
      check(renderedLevel[0] != null && expectedLevel.equals(renderedLevel[0].getName()),
          "Actual plan renderer selects " + expectedLevel);
      check(text.contains("Level: " + expectedLevel), "Printed header identifies " + expectedLevel);
      check(text.contains("Scale: 1/" + Math.round(1 / renderedScale[0])), "Printed scale matches fitted plan");
      check(!text.contains("Scale: 1/100"), "Multi-level header ignores obsolete fixed scale");
      check(home.getSelectedLevel() == selected, "Header metadata restores selected level");
    }
    failHeaderScale[0] = true;
    Graphics2D graphics = new BufferedImage(800, 1000, BufferedImage.TYPE_INT_RGB).createGraphics();
    try {
      try { printable.print(graphics, page(), 1); throw new AssertionError("Expected header scale failure"); }
      catch (IllegalStateException expected) {
        check(home.getSelectedLevel() == selected, "Header scale failure restores selected level");
      }
    } finally { graphics.dispose(); }
  }
  private static class CapturingTable extends FurnitureTable {
    int printedRows, printedLevelColumns; boolean fail;
    CapturingTable(Home home) { super(home, PREFS); }
    @Override public Printable getPrintable(JTable.PrintMode mode, MessageFormat header, MessageFormat footer) {
      return (graphics, format, index) -> {
        printedRows = getRowCount(); printedLevelColumns = 0;
        for (int c = 0; c < getColumnCount(); c++)
          if (getColumnModel().getColumn(c).getIdentifier() == HomePieceOfFurniture.SortableProperty.LEVEL)
            printedLevelColumns++;
        if (fail) throw new PrinterException("Injected print failure");
        return Printable.PAGE_EXISTS;
      };
    }
  }
}
