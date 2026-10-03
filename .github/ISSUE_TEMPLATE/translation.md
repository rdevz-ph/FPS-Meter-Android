---
name: Translation Submission or Request
about: Submit a new language translation or report translation improvements
title: "[Translation]: <Language Name> (<Language Code>)"
labels: ["translation", "i18n"]
assignees: ""
---

### Language Information

- **Language Name:** (for example: Spanish, Japanese, German)
- **Language Code (ISO 639-1):** (for example: es, ja, de)
- **Type of Contribution:** (New translation / Fix existing translation)

### Translation Content

If you have translated strings, you can paste your translated XML content or attach your `strings.xml` file below:

```xml
<!-- Paste your translated strings here -->
```

### Checklist

- [ ] All string names match the original `app/src/main/res/values/strings.xml`
- [ ] Variables and placeholders like `%s`, `%d`, `%1$s` are preserved
- [ ] Special characters like apostrophes are escaped with backslashes (`\'`)
- [ ] Brand and technical terms (FPS Meter, Shizuku, SurfaceFlinger, Vulkan) are kept in original form

### Additional Comments
(Add any notes or context about your translations here)
