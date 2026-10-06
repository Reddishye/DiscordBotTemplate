# Manual test checklist

How to check the menu system by hand, once a test bot runs the showcase menu.

The showcase (`es.redactado.menu.examples.ShowcaseMenu`) is compiled and tested but
**never registered by default**. Neither are the three simple-menu examples, listed in
the last section. The framework now ships wired: `MenuService` starts
with the bot and `MenuListener` routes interactions to it. What it does **not** ship
is a way to open a menu, because that is the developer's decision. The section
"Opening the showcase" at the end gives you a throwaway command; until you add it,
there is nothing to open and therefore nothing to test.

Run through the numbered items in order. Each one says what to press and what you
should see. Anything that does not match is a defect worth reporting with the
message id and the log line that went with it.

Throughout, the person who pressed the button is the **owner**. A second account
is the **stranger**, used in item 6.

---

## 1. Open the showcase and walk every view

1. Trigger whatever slash command or message command opens the showcase.
2. Press **Components**.
3. Press **Back**, and confirm you are back on the home view rather than somewhere else.
4. Press **Presets**, then **Back**.
5. Press **Confirmation**, then **Back**.
6. Press **Modal form**, then **Back**.

**Expected.** Every view replaces the contents of the same message rather than
posting a new one, and **Back always returns to the view you came from**, not to the
home view. Do step 3 twice: the first Back consumes one entry, the second lands on
home, and the third is silent. Nothing is posted twice, and no view shows an empty
container.

## 2. Compare the five built-in presets

On the **Presets** view, pick each preset in turn: `default`, `minimal`,
`midnight`, `vibrant`, `monochrome`. Then press **Components** and come back, so
you can see the same content under a different look.

Compare each against `docs/menus-inventory.md`:

| Preset | Header level | Subtitle | Icons | Divider | Spacing | Footer |
| --- | --- | --- | --- | --- | --- | --- |
| `default` | `###` (3) | no | yes | drawn | normal | none |
| `minimal` | `###` (3) | no | **none at all** | **not drawn** | compact | none |
| `midnight` | `##` (2) | **yes** | yes | drawn | roomy | menu name |
| `vibrant` | `#` (1) | **yes** | yes, large | drawn | roomiest | none |
| `monochrome` | `##` (2) | no | yes, greyscale | drawn | normal | none |

**Expected.** The header level matches the column, the subtitle appears only for
`midnight` and `vibrant`, `minimal` has no emoji anywhere and still renders every
button (buttons fall back to their label), and the accent colour of the container
border changes with every preset. `monochrome` renders in greys: the shapes still
say which button is which, the colours say nothing.

**Expected.** Choosing a preset changes **nothing outside this message**. Nobody
else's menus change, and after a restart the choice is gone.

## 3. Pager

On **Components**:

1. Press **Next** until the last page. **Expected:** the indicator reaches `5/5`
   and Next becomes disabled.
2. Press **Previous** back to `1/5`. **Expected:** Previous becomes disabled on
   the first page rather than disappearing, so the row keeps its width.
3. Double-click **Next** as fast as you can, several times.

**Expected.** One click advances exactly one page. A rapid second click is
swallowed: no error appears, and the page advances once rather than twice. That is
the per-message re-entrancy guard, and a visible "bot is busy" message here would
mean it is not working.

## 4. Confirmation

1. Press **Danger** on **Components**. **Expected:** a confirmation asking
   whether to remove an item, with a Confirm and a Cancel button, and no item has
   gone yet.
2. Press **Cancel**. **Expected:** back on the component view, the item list
   unchanged.
3. Press **Danger** again, then **Confirm**. **Expected:** back on the component
   view and one item fewer. Open the confirmation again to read the count.
4. Press **Back**. **Expected:** the home view.

**Expected.** Cancel never deletes anything, and pressing Back after answering
leaves the view you answered from, not somewhere else.

## 5. Modal form

1. On **Modal form**, press **Open the form**.
2. Fill in **Name** with something long, and **Why** with nothing.
3. Submit.

Then again:

4. Open the form and submit it **completely empty**.

**Expected.** Discord refuses a submit that breaks the form's own rules: an empty
**Name** cannot be submitted, and more than 40 characters in **Name** cannot be
submitted. A field marked optional (**Why**) accepts nothing.

**Expected.** After a successful submit the answers appear back in the view, with
the surrounding whitespace removed from what you typed. A long paragraph is cut
short rather than breaking the layout. **Expected:** the view updates in place,
the same message, no new message.

**Expected.** Opening the form is the only thing that happened: the button press
produced no "thinking..." state and no error, because a modal is the first and
only answer an interaction may get.

## 6. Another user pressing your buttons

With the showcase open as the owner, use the **stranger** account in the same
channel and press one of its buttons.

**Expected.** One ephemeral message, only visible to the stranger: *"This menu is
not yours."* **Expected:** the message does not change, and no handler runs. The
stranger's own menus work normally.

## 7. Restart the bot and use an old message

1. Open the showcase and navigate into **Components**.
2. Restart the bot.
3. Press **Back** on the old message.

**Expected.** One ephemeral message: *"This menu expired."* **Expected:** the
message then shows the home view, not an error and not an empty container. A
second **Back** on the same message is silent, because there is no history left
and the home view is already showing.

## 8. Spanish and English

1. Set the **owner's** Discord language to Spanish, then open a fresh showcase.
2. Press **Back** from a sub-view, and open a pager to see its arrows.
3. Set the language back to English and repeat.

**Expected.** With Spanish: *"Atrás"* on the back button, *"Anterior"* and
*"Siguiente"* on the pager arrows, *"Confirmar"* and *"Cancelar"* on the
confirmation, and the localized denial and expiry messages. With English: the
English words.

**Expected.** The showcase's own labels stay in English in both cases, because
the examples package is deliberately exempt from the bundle: it exists to show the
components. A real menu has no such exemption. This is the one place where the
showcase and a production menu will look different, and it is on purpose.

## 9. Direct message

Send the showcase's open command as a direct message to the bot, then walk the
views.

**Expected.** Everything works: no guild, no preferences and no locale of its own,
so the user language decides the language and the built-in `default` preset is
used. **Expected:** no exception in the log. `getGuildLocale()` throws in a
direct message, so any stack trace mentioning it here is a defect.

## 10. Custom preset and hot reload

1. Copy `docs/presets/ocean.json` into the presets directory the bot was
   configured with.
2. Wait a moment, then reopen the showcase and look for `ocean` on the
   **Presets** view.

**Expected.** `ocean` appears **without a restart**, and picking it shows a blue
accent. It inherits from `midnight`, so it keeps midnight's spacing and footer
unless the file overrides them.

3. Break the file: remove one comma, or set `"level": 9`.
4. Wait a moment, then look at the **Presets** view again.

**Expected.** The bot keeps running. The last good version stays in effect, and
one warning in the log names the file and the field, in the form
`ocean.json: header.level: expected 1..3, got 9`. **Expected:** the menu is
still usable throughout, and the broken preset does not appear.

## 11. Error reference code

1. Temporarily make a showcase handler throw, for example by editing
   `ShowcaseMenu#deleteAnItem` to throw an `IllegalStateException`.
2. Press the button that reaches it.

**Expected.** The user sees *"Something went wrong (ref: xxxxxx)."* and nothing
else: no exception class, no message, no stack trace. The stack trace goes to the
log only, at `ERROR`, with the same reference code so one can be matched to the
other.

**Expected.** The bot does not break, later clicks still work, and the same
failure does not print twice for one click.

## Opening the showcase

The framework **ships no commands**. Opening a menu is the developer's decision: a slash
command, another system's button, a modal. What ships is the primitive,
`MenuService.open`, and the router that `MenuListener` already feeds.

Nothing below is shipped code. It is a throwaway command of your own, written to the
template's real command conventions so it compiles as-is.

### 1. A command class

The class is already written and compiled, at
`src/test/java/es/redactado/command/ShowcaseCommand.java`. Copy it into
`src/main/java/es/redactado/command/` and add this line in
`TemplateBindings.contribute()`, next to `PingCommand`:

```java
slashCommand(ShowcaseCommand.class);
```

The command itself:

```java
public class ShowcaseCommand implements BaseSlashCommand {

    private final MenuService menuService;

    @Inject
    public ShowcaseCommand(MenuService menuService) {
        this.menuService = menuService;
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("showcase", "Open the menu showcase")
                .setNSFW(false)
                .setContexts(InteractionContextType.GUILD, InteractionContextType.BOT_DM);
    }

    @Override
    public boolean ephemeral() {
        return true;
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        menuService.register(new ShowcaseMenu(menuService.presets()));
        menuService.open(event, "showcase", true);
    }
}
```

### 2. What this does, and what it does not

- **Registration happens on the first click.** `register` is idempotent per id but throws
  on a duplicate, so guard it if you expect repeated use; the snippet above is fine for a
  test bot and wrong for a busy one.
- **The second argument is ephemeral.** `true` means only the person who ran the command
  sees the menu. Pass `false` for a shared board.
- **The showcase reads `menuService.presets()`** so the preset picker lists your custom
  presets as well as the five built-in ones.
- **Remove the line and the copied class when you are done.** The showcase is an example.

### 3. The simple-menu examples

The three examples in `es.redactado.menu.examples` are **not registered either**. To
walk them by hand, register whichever you want in the same command and open it by
id. Each is built by a static method, so this is a one-line change to the command
above:

```java
import es.redactado.menu.examples.CounterMenu;
import es.redactado.menu.examples.HelpMenu;
import es.redactado.menu.examples.ServerInfoMenu;

// HelpMenu.build()                -> "help",          two views, a link, Back
menuService.register(CounterMenu.build());
menuService.register(ServerInfoMenu.build(new ServerInfoMenu.FakeService(400)));
menuService.open(event, "counter", true);
menuService.open(event, "server_info", true);
```

| Example | Open by id | What to press |
| --- | --- | --- |
| `HelpMenu.build()` | `help` | **FAQ**, then **Back**. The **Docs** button should open your browser and touch no message. |
| `CounterMenu.build()` | `counter` | **+1** three times, then **-1** once. **Reset** must only ask. Press **Back** to cancel and the count must not change, then press Reset again and accept. |
| `ServerInfoMenu.build(new FakeService(400))` | `server_info` | Page the member list with the arrows. Pick **Members** and then **Roles** in the select: the text under the list changes and **nothing is added to the history**, so there is no Back. |

The `FakeService` delay is deliberate: it proves the load runs off the JDA event
thread. Lower it to `0` if you want the menu to feel instant, and delete the three
examples when you are done, like the showcase.

### 4. Which manual-test steps this makes possible

**Steps 1 to 11 above**, all of them. Before this snippet nothing in the checklist was
reachable, because there was no way to open a menu; with it, the only thing standing
between you and a test run is a restart. Section 3 adds the three simple-menu examples
to the same run.

---

## Not testable with mocks

Everything above needs a real bot, because each item is about something a stub
cannot tell you. A mocked interaction has no client, so there is no layout to
look at; no rendering, so there is nothing to compare against a screenshot; no
network, so no rate limit, no latency and no three-second acknowledgement budget;
no filesystem, so no preset watcher; no restart, so no expiry; and no second
account, so no ownership check by anyone else's hand.

Three items in particular are worth doing by hand even if everything else is
automated later:

- **Item 3, the rapid double click.** The guard is unit tested with two mock
  events, which proves one handler ran. Only a real client shows whether the
  button *looks* like it did nothing.
- **Item 5, the modal's own validation.** Discord rejects a too-long or empty
  required field before the bot ever sees it. No mock can reproduce a rejection
  the bot did not send.
- **Item 10, hot reload.** The watcher needs a real `WatchService`, real file
  writes and real time. Its automated test is tagged `filesystem` for that reason
  and is skipped with `./gradlew test -PexcludeTags=filesystem`.