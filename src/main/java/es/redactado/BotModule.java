package es.redactado;

import static es.redactado.config.Database.REPOSITORIES;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import es.redactado.database.DatabaseManager;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.sharding.ShardManager;

public class BotModule extends AbstractModule {
    private Main main;
    private final ShardManager shardManager;

    public BotModule(Main main, ShardManager shardManager) {
        this.main = main;
        this.shardManager = shardManager;
    }

    @Override
    protected void configure() {
        // Main
        bind(Main.class).toInstance(main);
        bind(ShardManager.class).toInstance(shardManager);

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
