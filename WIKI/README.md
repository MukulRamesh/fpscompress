# FPSCompress Wiki Content

This folder contains markdown files for the FPSCompress GitHub wiki.

## Contents

1. **[Home.md](Home.md)** - Wiki homepage with overview and quick links
2. **[Getting-Started.md](Getting-Started.md)** - First-time setup guide
3. **[PreFab-System.md](PreFab-System.md)** - Detailed PreFab system documentation
4. **[Importer-Exporter-Guide.md](Importer-Exporter-Guide.md)** - Importer/Exporter setup and usage
5. **[Face-Configuration.md](Face-Configuration.md)** - Face configuration GUI walkthrough
6. **[Cached-Production.md](Cached-Production.md)** - How cached production and fractional math works
7. **[State-Machine-Guide.md](State-Machine-Guide.md)** - Understanding BUILDING/SIMULATING/CACHED/HALTED states
8. **[Troubleshooting.md](Troubleshooting.md)** - Common issues and solutions
9. **[Advanced-Setup.md](Advanced-Setup.md)** - Multi-PreFab setups and advanced techniques
10. **[Developer-API.md](Developer-API.md)** - API documentation for mod developers

## Publishing to GitHub Wiki

### Option 1: Manual Upload (Simplest)

1. Go to your GitHub repository's wiki: `https://github.com/USERNAME/FPSCompress/wiki`
2. For each markdown file:
   - Click "New Page"
   - Name the page (e.g., "Getting Started" for `Getting-Started.md`)
   - Copy/paste the markdown content
   - Save

### Option 2: Git Clone and Push

GitHub wikis have a separate git repository:

```bash
# Clone wiki repository
git clone https://github.com/MukulRamesh/FPSCompress.wiki.git

# Copy wiki files
cp WIKI/*.md FPSCompress.wiki/

# Commit and push
cd FPSCompress.wiki
git add .
git commit -m "Initial wiki content"
git push
```

### Option 3: GitHub Actions (Automated)

See `.github/workflows/sync-wiki.yml` (if created) for automated sync on push to `WIKI/` folder.

## Internal Links

Wiki pages use internal links that work on GitHub wiki:

```markdown
[Link Text](Page-Name)
```

For example:
- `[Getting Started](Getting-Started)` → Links to Getting-Started page
- `[PreFab System](PreFab-System)` → Links to PreFab-System page

**Note**: GitHub wiki automatically converts page names:
- `Getting-Started.md` → Displayed as "Getting Started" in wiki
- Internal link: `[text](Getting-Started)` works

## Maintenance

### Updating Content

1. **Edit markdown files** in `WIKI/` folder (local repository)
2. **Commit changes** to main repository
3. **Sync to wiki**:
   - Manual: Copy/paste updated content to wiki pages
   - Git: Pull wiki repo, copy files, push
   - Actions: Automatic if workflow is set up

### Keeping in Sync with Code

When features change:
1. Update `NOTES/` architecture docs (source of truth)
2. Update `WIKI/` markdown files (public-facing docs)
3. Update Patchouli entries if applicable
4. Push changes to GitHub

### Adding New Pages

1. Create new `.md` file in `WIKI/`
2. Add link in `Home.md` (table of contents)
3. Add internal links from related pages
4. Publish to GitHub wiki

## Source Attribution

Wiki content derived from:
- `NOTES/ARCHITECTURE_CONDUIT.md` - Technical specifications
- `NOTES/README_ARCHITECTURE.md` - Player experience
- `NOTES/IMPORTER_EXPORTER_SYSTEM.md` - Three-block system
- `NOTES/CLAUDE.md` - Project guidelines
- `README.md` - Project overview

Content has been simplified and reorganized for end-user consumption.

## License

This documentation is part of the FPSCompress project and is licensed under the MIT License.
