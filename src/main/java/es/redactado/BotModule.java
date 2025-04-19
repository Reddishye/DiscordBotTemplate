package es.redactado;

import com.google.inject.AbstractModule;
import io.github.cdimascio.dotenv.Dotenv;

public class BotModule extends AbstractModule {
    private Main main;

    public BotModule(Main main) {
        this.main = main;
    }

    @Override
    protected void configure() {
        // Main
        bind(Main.class).toInstance(main);
        bind(Dotenv.class).toInstance(Dotenv.load());
    }
}
