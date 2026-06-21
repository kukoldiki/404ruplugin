package main.java.esco.accent;

public class OwOAccent implements Accent {
    @Override
    public String getName() {
        return "OwO";
    }

    @Override
    public String apply(String message) {
        message = message.replace("р", "в").replace("л", "в")
                .replace("Р", "В").replace("Л", "В")
                .replace("l", "w").replace("r", "w")
                .replace("L", "W").replace("R", "W")
                .replace(" ?", " OwO")
                .replace("?", " OwO")
                .replace(":(", "TwT")
                .replace(">:)", ">:3")
                .replace(" !", " >W<")
                .replace("!", " >W<");

        if(Math.random() > 0.8)
            message = message + " nyyaaae~";

        return message;
    }
}
