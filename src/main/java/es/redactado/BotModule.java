package es.redactado;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import es.redactado.database.DatabaseManager;
import io.github.cdimascio.dotenv.Dotenv;

import static es.redactado.config.Database.REPOSITORIES;

public class BotModule extends AbstractModule {
    private Main main;

    public BotModule(Main main) {
        this.main = main;
    }

    @Override
    protected void configure() {
        // Main
        bind(Main.class).toInstance(main);

        // Database
        bind(DatabaseManager.class).asEagerSingleton();

        // Repositories
        for (Class<?> clazz : REPOSITORIES) {
            bind(clazz).in(Singleton.class);
        }
    }

    @Provides
    @Singleton
    public Dotenv provideDotenv() {
        return Dotenv.configure().load();
    }
}
