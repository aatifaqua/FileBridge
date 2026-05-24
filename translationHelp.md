# Help Translate FileBridge

Thank you for wanting to translate FileBridge! Translations make the app accessible to more people.

## Where strings live

All user-visible strings are in:

```
app/src/main/res/values/strings.xml   ← English (source of truth)
app/src/main/res/values-<locale>/strings.xml  ← translated files
```

## How to add a new locale

1. Create the folder `app/src/main/res/values-<locale>/` using the correct BCP 47 language tag.  
   Examples: `values-de/` (German), `values-fr/` (French), `values-zh-rCN/` (Simplified Chinese).

2. Copy `app/src/main/res/values/strings.xml` into the new folder.

3. Translate every `<string>` value. **Do not change the `name` attribute** — only the text content.

4. Leave `translatable="false"` strings (like app-internal IDs) untouched, or omit them entirely.

5. Add your locale to `app/src/main/res/xml/locale_config.xml`:
   ```xml
   <locale android:name="de" />
   ```

6. Open a PR with the changes (see [CONTRIBUTING.md](CONTRIBUTING.md)).

## Translation platform

We plan to use **Weblate** for community translations. The project URL will be available at:

```
https://hosted.weblate.org/projects/filebridge/
```

*(Link added once the project is created — check the Settings → About screen in the app for the current URL.)*

## Tips

- Keep placeholders like `%1$s` and `%1$d` intact — they insert dynamic values at runtime.
- Respect HTML markup in strings if present (e.g. `<b>`, `<br/>`).
- Aim for natural, conversational phrasing rather than word-for-word translation.
- If a term has no good translation (e.g. "FTP", "FTPS"), keep it in English.

## Questions?

Open a [GitHub Issue](https://github.com/aionyxe/FileBridge/issues) labelled **translation**.
