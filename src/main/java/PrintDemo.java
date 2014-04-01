import javax.print.*;
import javax.print.attribute.*;
import javax.print.attribute.standard.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * Created by roberto on 3/20/2014.
 */
public class PrintDemo {

    public static void main(String[] args) {

        // Set up our document attributes
        DocAttributeSet docAttributeSet =
                new HashDocAttributeSet() {{
                    add(Chromaticity.COLOR);
                }};

        PrintServiceAttributeSet printServiceAttributeSet =
                new HashPrintServiceAttributeSet() {{
                    add(new PrinterName("Officejet_6500_E709n", Locale.US));

                }};

        PrintService[] printServices =
                PrintServiceLookup.lookupPrintServices(
                        DocFlavor.INPUT_STREAM.AUTOSENSE, printServiceAttributeSet);

        PrintService printService = printServices[0];

        try {

            FileInputStream fileInputStream =
                    new FileInputStream("/Users/Roberto/Dropbox/docs/PDF/semi-log-graph-paper.pdf");

            SimpleDoc doc =
                    new SimpleDoc(fileInputStream, DocFlavor.INPUT_STREAM.PDF,
                            docAttributeSet);

            DocPrintJob printJob = printService.createPrintJob();

            PrintRequestAttributeSet printRequestAttributeSet = new HashPrintRequestAttributeSet() {{
                add(new Copies(5));
                add(MediaTray.MAIN);
                add(Chromaticity.COLOR);
                add(OrientationRequested.LANDSCAPE);
            }};

            printJob.print(doc, printRequestAttributeSet);

        } catch (IOException ignored) {
        } catch (PrintException ignored) {
        }
    }
}
