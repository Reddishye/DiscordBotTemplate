package es.redactado.menu.core;

import es.redactado.menu.api.Context;
import es.redactado.menu.api.Menu;
import es.redactado.menu.view.Validator;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IModalCallback;
import net.dv8tion.jda.api.modals.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractMenu implements Menu {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    private final String id;

    protected AbstractMenu(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Container render(Context ctx) {
        return build(ctx);
    }

    protected abstract Container build(Context ctx);

    @Override
    public void onButton(Context ctx, ButtonInteractionEvent event) {
        if ("nav".equals(ctx.action())) handleBack(ctx, event);
    }

    @Override
    public void onModal(Context ctx, ModalInteractionEvent event) {}

    protected Container renderValidated(Context ctx) {
        return Validator.verify(render(ctx));
    }

    /** Re-render and edit message. For ButtonInteraction: direct edit; for Modal: deferEdit + hook. */
    protected void refresh(Context ctx) {
        var container = render(ctx);
        var event = ctx.event();
        if (event instanceof ButtonInteractionEvent be) {
            be.editComponents(container).useComponentsV2().queue();
        } else if (event instanceof ModalInteractionEvent me) {
            me.deferEdit()
                    .queue(
                            hook ->
                                    hook.editOriginalComponents(container)
                                            .useComponentsV2()
                                            .queue());
        }
    }

    /** Reply with a modal. */
    protected void showModal(Context ctx, Modal modal) {
        if (ctx.event() instanceof IModalCallback mc) mc.replyModal(modal).queue();
    }

    protected void handleBack(Context ctx, ButtonInteractionEvent event) {
        ctx.pop()
                .ifPresentOrElse(
                        prev -> {
                            event.deferEdit().queue();
                            event.getHook()
                                    .editOriginalComponents(render(prev))
                                    .useComponentsV2()
                                    .queue();
                        },
                        () -> event.reply("No previous menu.").setEphemeral(true).queue());
    }
}
