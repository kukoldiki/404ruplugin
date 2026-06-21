package main.java.esco.accent;

public class GreloAccent implements Accent {
    public String getName() {
        return "Grelo";
    }

    @Override
    public String apply(String message) {
        message = message.replace("блин", "рыбьи головэжки")
                .replace("ой", "аэ")
                .replace("да", "айе")
                .replace("я", "йа")
                .replace("рпшер", "гей")
                .replace("пизда", "гаддэм")
                .replace("бля", "гаддэм")
                .replace("БЛЯЯ", "ГАДДЭМ")
                .replace("женщины", "страшные люди")
                .replace("реально", "фор рил, брооо")
                .replace("не", "зачем")
                .replace("брат", "свояк")
                .replace("гг", "это такой конец...")
                .replace("урод", "нет ризза")
                .replace("тупой", "засранец")
                .replace("хорошо", "сооу куул, броо")
                .replace("блять", "фак тзис щит")
                .replace("сука", "бич")
                .replace("ерп", "ужас")
                .replace("мужик", "мужлан")
                .replace("ок", "йоооу")
                .replace("нормально", "улёт")
                .replace("говно", "щит")
                .replace("мда", "брух")
                .replace("гей", "мужеложец");

        if(Math.random() > 0.8)
            message = "Щас бы дорожку белую... " + message;
        if(Math.random() > 0.8)
            message = message + "гаддэээм...";

        return message;
    }
}
