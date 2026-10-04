package es.redactado.menu.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ActionTableTest {

    private static ButtonHandler button() {
        return (ctx, event) -> Done.NOW;
    }

    private static ModalHandler modal() {
        return (ctx, event) -> Done.NOW;
    }

    @Nested
    @DisplayName("lookup")
    class Lookup {

        @Test
        @DisplayName("finds a declared button action")
        void findsButton() {
            ButtonHandler handler = (ctx, event) -> Done.NOW;
            ActionTable table =
                    ActionTable.builder().button("edit", Ack.DEFER_EDIT, handler).build();

            assertThat(table.button("edit")).contains(new ButtonAction(Ack.DEFER_EDIT, handler));
            assertThat(table.button("missing")).isEmpty();
        }

        @Test
        @DisplayName("finds a declared modal action")
        void findsModal() {
            ActionTable table =
                    ActionTable.builder().modal("submit", Ack.DEFER_EDIT, modal()).build();

            assertThat(table.modal("submit")).isPresent();
            assertThat(table.modal("missing")).isEmpty();
        }

        @Test
        @DisplayName("counts declared actions")
        void counts() {
            ActionTable table =
                    ActionTable.builder()
                            .button("a", Ack.DEFER_EDIT, button())
                            .button("b", Ack.NONE, button())
                            .modal("c", Ack.DEFER_REPLY, modal())
                            .build();

            assertThat(table.buttonCount()).isEqualTo(2);
            assertThat(table.modalCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("keeps button and modal namespaces separate")
        void namespacesAreSeparate() {
            ActionTable table =
                    ActionTable.builder()
                            .button("shared", Ack.DEFER_EDIT, button())
                            .modal("shared", Ack.DEFER_REPLY, modal())
                            .build();

            assertThat(table.button("shared")).isPresent();
            assertThat(table.modal("shared")).isPresent();
            assertThat(table.button("shared").orElseThrow().ack()).isEqualTo(Ack.DEFER_EDIT);
            assertThat(table.modal("shared").orElseThrow().ack()).isEqualTo(Ack.DEFER_REPLY);
        }

        @Test
        @DisplayName("is not affected by later changes to the builder")
        void tableIsImmutable() {
            ActionTable.Builder builder =
                    ActionTable.builder().button("a", Ack.DEFER_EDIT, button());
            ActionTable first = builder.build();
            builder.button("b", Ack.DEFER_EDIT, button());
            ActionTable second = builder.build();

            assertThat(first.buttonCount()).isEqualTo(1);
            assertThat(second.buttonCount()).isEqualTo(2);
            assertThat(first.button("b")).isEmpty();
        }
    }

    @Nested
    @DisplayName("builder validation")
    class Validation {

        @Test
        @DisplayName("rejects an empty button name")
        void rejectsEmptyButtonName() {
            assertThatThrownBy(() -> ActionTable.builder().button("", Ack.NONE, button()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be empty");
        }

        @Test
        @DisplayName("rejects an empty modal name")
        void rejectsEmptyModalName() {
            assertThatThrownBy(() -> ActionTable.builder().modal("", Ack.NONE, modal()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be empty");
        }

        @Test
        @DisplayName("rejects a colon in a button name")
        void rejectsColonInButtonName() {
            assertThatThrownBy(() -> ActionTable.builder().button("a:b", Ack.NONE, button()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("a:b");
        }

        @Test
        @DisplayName("rejects a colon in a modal name")
        void rejectsColonInModalName() {
            assertThatThrownBy(() -> ActionTable.builder().modal("a:b", Ack.NONE, modal()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("a:b");
        }

        @Test
        @DisplayName("rejects a null ack on a button action")
        void rejectsNullButtonAck() {
            assertThatThrownBy(() -> ActionTable.builder().button("edit", null, button()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("edit");
        }

        @Test
        @DisplayName("rejects a null handler on a button action")
        void rejectsNullButtonHandler() {
            assertThatThrownBy(() -> ActionTable.builder().button("edit", Ack.NONE, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("edit");
        }

        @Test
        @DisplayName("rejects a null ack on a modal action")
        void rejectsNullModalAck() {
            assertThatThrownBy(() -> ActionTable.builder().modal("submit", null, modal()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("submit");
        }

        @Test
        @DisplayName("rejects a null handler on a modal action")
        void rejectsNullModalHandler() {
            assertThatThrownBy(() -> ActionTable.builder().modal("submit", Ack.NONE, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("submit");
        }

        @Test
        @DisplayName("rejects a duplicate button name")
        void rejectsDuplicateButton() {
            ActionTable.Builder builder =
                    ActionTable.builder().button("edit", Ack.DEFER_EDIT, button());

            assertThatThrownBy(() -> builder.button("edit", Ack.NONE, button()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("edit")
                    .hasMessageContaining("Duplicate");
        }

        @Test
        @DisplayName("rejects a duplicate modal name")
        void rejectsDuplicateModal() {
            ActionTable.Builder builder =
                    ActionTable.builder().modal("submit", Ack.DEFER_EDIT, modal());

            assertThatThrownBy(() -> builder.modal("submit", Ack.DEFER_EDIT, modal()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("submit")
                    .hasMessageContaining("Duplicate");
        }

        @Test
        @DisplayName("rejects a modal action declaring Ack.MODAL")
        void rejectsModalAckOnModalAction() {
            assertThatThrownBy(() -> ActionTable.builder().modal("submit", Ack.MODAL, modal()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("submit")
                    .hasMessageContaining("Ack.MODAL");
        }

        @Test
        @DisplayName("rejects a null action name")
        void rejectsNullName() {
            assertThatThrownBy(() -> ActionTable.builder().button(null, Ack.NONE, button()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("accepts Ack.MODAL on a button action")
        void acceptsModalAckOnButton() {
            ActionTable table = ActionTable.builder().button("open", Ack.MODAL, button()).build();

            assertThat(table.button("open").orElseThrow().ack()).isEqualTo(Ack.MODAL);
        }
    }

    @Nested
    @DisplayName("Done")
    class DoneTest {

        @Test
        @DisplayName("NOW is already completed and reusable")
        void nowIsCompleted() {
            CompletableFuture<Void> first = Done.NOW;
            CompletableFuture<Void> second = Done.NOW;

            assertThat(first).isCompleted();
            assertThat(first).isSameAs(second);
        }
    }
}
