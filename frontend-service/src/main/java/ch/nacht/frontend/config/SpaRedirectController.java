package ch.nacht.frontend.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Liefert bei einem direkten Aufruf einer Angular-Route die {@code index.html} aus.
 *
 * <p>Ohne das beantwortet Spring Boot einen Neuladen-Klick auf {@code /tarife} oder
 * {@code /nebenkosten/abrechnung} mit seiner Whitelabel-Fehlerseite: Der Router von Angular lebt
 * im Browser, der Server kennt diese Pfade nicht.
 *
 * <p><b>Jedes Segment muss punktfrei sein.</b> Damit bleiben Anfragen nach echten Dateien
 * ({@code /main.js}, {@code /assets/logo.png}) bei der statischen Auslieferung und ergeben bei
 * einem Tippfehler weiterhin ein ehrliches 404 — statt einer HTML-Seite, die sich als Bild ausgibt.
 *
 * <p><b>Die Verschachtelungstiefe ist bewusst aufgezählt</b> statt über {@code /**} erschlagen:
 * Ein Sammelmuster würde auch {@code /assets/logo.png} fangen, weil nur das erste Segment geprüft
 * würde. Drei Ebenen decken alle Routen ab (tiefste heute: {@code /nebenkosten/abrechnung}); eine
 * vierte Ebene ist hier zu ergänzen.
 */
@Controller
public class SpaRedirectController {

    @RequestMapping({
            "/{eins:[^\\.]*}",
            "/{eins:[^\\.]*}/{zwei:[^\\.]*}",
            "/{eins:[^\\.]*}/{zwei:[^\\.]*}/{drei:[^\\.]*}"
    })
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
