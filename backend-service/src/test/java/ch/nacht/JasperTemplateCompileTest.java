package ch.nacht;

import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class JasperTemplateCompileTest {

    @Test
    void testRechnungTemplateCompiles() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/reports/rechnung.jrxml");
        assertNotNull(stream, "rechnung.jrxml not found");
        JasperReport report = JasperCompileManager.compileReport(stream);
        assertNotNull(report);
        System.out.println("rechnung.jrxml compiled successfully");
    }

    @Test
    void testStatistikTemplateCompiles() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/reports/statistik.jrxml");
        assertNotNull(stream, "statistik.jrxml not found");
        JasperReport report = JasperCompileManager.compileReport(stream);
        assertNotNull(report);
        System.out.println("statistik.jrxml compiled successfully");
    }

    @Test
    void testEinheitSummenTemplateCompiles() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/reports/einheit-summen.jrxml");
        assertNotNull(stream, "einheit-summen.jrxml not found");
        JasperReport report = JasperCompileManager.compileReport(stream);
        assertNotNull(report);
        System.out.println("einheit-summen.jrxml compiled successfully");
    }
}
