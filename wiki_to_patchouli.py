#!/usr/bin/env python3
"""
Convert WIKI markdown files to Patchouli in-game documentation format.
"""

import os
import re
import json
from pathlib import Path
from typing import Dict, List, Tuple, Optional

# Configuration
WIKI_DIR = Path("WIKI")
OUTPUT_BASE = Path("fpscompress-template-1.21.11/src/main/resources/assets/fpscompress/patchouli_books/fpscompress_guide/en_us")
CATEGORIES_DIR = OUTPUT_BASE / "categories"
ENTRIES_DIR = OUTPUT_BASE / "entries"

# Files to skip
SKIP_FILES = ["README.md", "Developer-API.md"]

# Category configuration (order matters)
CATEGORY_CONFIG = [
    {"file": "Home.md", "id": "welcome", "name": "Welcome", "description": "Welcome to FPSCompress!", "icon": "minecraft:book", "sortnum": 0},
    {"file": "Getting-Started.md", "id": "getting_started", "name": "Getting Started", "description": "Learn the basics of FPSCompress", "icon": "fpscompress:prefab_machine", "sortnum": 1},
    {"file": "PreFab-System.md", "id": "prefab_system", "name": "PreFab System", "description": "Core mechanics and how PreFabs work", "icon": "fpscompress:prefab_machine", "sortnum": 2},
    {"file": "Face-Configuration.md", "id": "face_configuration", "name": "Face Configuration", "description": "Configure PreFab faces and modes", "icon": "minecraft:compass", "sortnum": 3},
    {"file": "Importer-Exporter-Guide.md", "id": "importer_exporter_guide", "name": "Importers & Exporters", "description": "Input and output gate mechanics", "icon": "fpscompress:importer", "sortnum": 4},
    {"file": "State-Machine-Guide.md", "id": "state_machine_guide", "name": "State Machine", "description": "PreFab states and transitions", "icon": "minecraft:clock", "sortnum": 5},
    {"file": "Cached-Production.md", "id": "cached_production", "name": "Cached Production", "description": "Fractional math and caching system", "icon": "minecraft:hopper", "sortnum": 6},
    {"file": "Advanced-Setup.md", "id": "advanced_setup", "name": "Advanced Setup", "description": "Complex factory patterns and optimization", "icon": "minecraft:nether_star", "sortnum": 7},
    {"file": "Troubleshooting.md", "id": "troubleshooting", "name": "Troubleshooting", "description": "Common issues and solutions", "icon": "minecraft:redstone_torch", "sortnum": 8},
]


class MarkdownParser:
    """Parse markdown files into structured format."""

    def __init__(self, content: str, filename: str):
        self.content = content
        self.filename = filename
        self.lines = content.split('\n')

    def parse(self) -> Dict:
        """Parse markdown content and return structured data."""
        h1_title = None
        h2_sections = []
        current_h2 = None
        current_h2_content = []

        i = 0
        while i < len(self.lines):
            line = self.lines[i]

            # H1 header
            if line.startswith('# ') and not line.startswith('## '):
                h1_title = line[2:].strip()

            # H2 header (new section)
            elif line.startswith('## ') and not line.startswith('### '):
                # Save previous H2 section if exists
                if current_h2:
                    h2_sections.append({
                        'title': current_h2,
                        'content': '\n'.join(current_h2_content).strip()
                    })

                # Start new H2 section
                current_h2 = line[3:].strip()
                current_h2_content = []

            # Content (belongs to current H2)
            elif current_h2:
                current_h2_content.append(line)

            i += 1

        # Save last H2 section
        if current_h2:
            h2_sections.append({
                'title': current_h2,
                'content': '\n'.join(current_h2_content).strip()
            })

        return {
            'filename': self.filename,
            'h1_title': h1_title or 'Untitled',
            'h2_sections': h2_sections
        }


class PatchouliConverter:
    """Convert markdown content to Patchouli format."""

    def __init__(self, cross_ref_map: Dict[str, str]):
        self.cross_ref_map = cross_ref_map

    def to_snake_case(self, text: str) -> str:
        """Convert text to snake_case."""
        # Remove special characters except spaces and hyphens
        text = re.sub(r'[^\w\s-]', '', text)
        # Replace spaces and hyphens with underscores
        text = re.sub(r'[-\s]+', '_', text)
        # Convert to lowercase
        return text.lower()

    def convert_formatting(self, text: str) -> str:
        """Convert markdown formatting to Patchouli format codes."""
        if not text:
            return ""

        # Replace special characters
        text = text.replace('✅', '[DONE]')
        text = text.replace('❌', '[X]')
        text = text.replace('🔨', '[WIP]')
        text = text.replace('💡', 'Tip:')

        # Convert bold: **text** -> $(bold)text$()
        text = re.sub(r'\*\*(.+?)\*\*', r'$(bold)\1$()', text)

        # Convert italic: *text* -> $(italic)text$()
        # But not if it's in the middle of a word or already part of bold
        text = re.sub(r'(?<!\w)\*([^\*]+?)\*(?!\w)', r'$(italic)\1$()', text)

        # Convert links: [text](target) -> $(l:category/entry)text$() or plain text for external
        def replace_link(match):
            link_text = match.group(1)
            link_target = match.group(2)

            # Check if it's an external link (http/https)
            if link_target.startswith('http://') or link_target.startswith('https://'):
                return f"{link_text} (see wiki)"

            # Internal link - look up in cross-reference map
            if link_target in self.cross_ref_map:
                patchouli_ref = self.cross_ref_map[link_target]
                return f"$(l:{patchouli_ref}){link_text}$()"
            else:
                # Unknown link, keep as plain text
                return link_text

        text = re.sub(r'\[([^\]]+)\]\(([^\)]+)\)', replace_link, text)

        # Convert lists
        lines = text.split('\n')
        converted_lines = []
        for line in lines:
            stripped = line.strip()
            # Numbered lists: 1. item -> $(li)item
            if re.match(r'^\d+\.\s+', stripped):
                item_text = re.sub(r'^\d+\.\s+', '', stripped)
                converted_lines.append('$(li)' + item_text)
            # Bullet lists: - item or * item -> $(li)item
            elif stripped.startswith('- '):
                converted_lines.append('$(li)' + stripped[2:])
            elif stripped.startswith('* '):
                converted_lines.append('$(li)' + stripped[2:])
            else:
                converted_lines.append(line)

        text = '\n'.join(converted_lines)

        # Convert paragraph breaks (double newline) to Patchouli breaks
        # First, normalize multiple newlines
        text = re.sub(r'\n\n+', '\n\n', text)
        # Then convert double newlines to $(br2)
        text = text.replace('\n\n', '$(br2)')
        # Single newlines become spaces (Patchouli flows text)
        text = re.sub(r'(?<!\$\(br2\))\n(?!\$\(br2\))', ' ', text)

        return text.strip()

    def convert_table_to_list(self, content: str) -> str:
        """Convert markdown tables to formatted lists."""
        lines = content.split('\n')
        result = []
        in_table = False
        headers = []

        for line in lines:
            stripped = line.strip()

            # Detect table header
            if '|' in stripped and not in_table:
                headers = [h.strip() for h in stripped.split('|') if h.strip()]
                in_table = True
                continue

            # Skip separator line
            if in_table and re.match(r'^\|?[\s\-:|]+\|?$', stripped):
                continue

            # Process table row
            if in_table and '|' in stripped:
                cells = [c.strip() for c in stripped.split('|') if c.strip()]
                if len(cells) > 0:
                    # First cell is usually the label
                    if len(cells) == 1:
                        result.append(f"$(li)$(bold){cells[0]}$()")
                    else:
                        result.append(f"$(li)$(bold){cells[0]}:$() {' - '.join(cells[1:])}")
                continue

            # End of table
            if in_table and '|' not in stripped:
                in_table = False
                result.append('')

            # Regular line
            if not in_table:
                result.append(line)

        return '\n'.join(result)

    def clean_code_blocks(self, content: str) -> str:
        """Remove or simplify code blocks."""
        # Remove code block markers but keep content
        content = re.sub(r'```[\w]*\n', '', content)
        content = re.sub(r'```', '', content)
        return content

    def split_into_pages(self, content: str, entry_title: str) -> List[Dict]:
        """Split content into pages based on H3 headers and word count."""
        # First, convert tables
        content = self.convert_table_to_list(content)

        # Clean code blocks
        content = self.clean_code_blocks(content)

        # Ensure content starts with newline for regex to match first H3
        if not content.startswith('\n'):
            content = '\n' + content

        # Split by H3 headers (### Header)
        # Pattern: newline, ###, space, text, newline
        parts = re.split(r'\n### (.+?)\n', content)

        pages = []

        # First part (before any H3)
        # Only include if there's substantial content (not just whitespace)
        if parts[0].strip() and len(parts[0].strip()) > 10:
            text = self.convert_formatting(parts[0].strip())
            if text:
                pages.append({
                    "type": "text",
                    "text": text
                })

        # Process H3 sections
        for i in range(1, len(parts), 2):
            if i + 1 < len(parts):
                h3_title = parts[i].strip()
                h3_content = parts[i + 1].strip()

                text = self.convert_formatting(h3_content)
                if text:
                    pages.append({
                        "type": "text",
                        "title": h3_title,
                        "text": text
                    })

        # If no pages were created, create one from the whole content
        if not pages:
            text = self.convert_formatting(content)
            if text:
                pages.append({
                    "type": "text",
                    "text": text
                })

        return pages


def build_cross_reference_map(parsed_files: List[Dict], category_config: List[Dict]) -> Dict[str, str]:
    """Build a mapping from markdown links to Patchouli references."""
    cross_ref_map = {}

    # Build filename to category_id mapping
    file_to_category = {cat['file']: cat['id'] for cat in category_config}

    for parsed in parsed_files:
        filename = parsed['filename']
        category_id = file_to_category.get(filename)

        if not category_id:
            continue

        # Map the file name (without extension) to the first entry
        page_name = filename.replace('.md', '')

        # For each H2 section, create a mapping
        for idx, section in enumerate(parsed['h2_sections']):
            entry_id = PatchouliConverter({}).to_snake_case(section['title'])

            # Map: "Page-Name" -> "category/first_entry"
            # Map: "Page-Name#Section-Title" -> "category/entry"
            if idx == 0:
                cross_ref_map[page_name] = f"{category_id}/{entry_id}"

            cross_ref_map[f"{page_name}#{section['title'].replace(' ', '-')}"] = f"{category_id}/{entry_id}"

    return cross_ref_map


def generate_categories(category_config: List[Dict], output_dir: Path):
    """Generate category JSON files."""
    output_dir.mkdir(parents=True, exist_ok=True)

    for cat in category_config:
        category_json = {
            "name": cat['name'],
            "description": cat['description'],
            "icon": cat['icon'],
            "sortnum": cat['sortnum']
        }

        output_file = output_dir / f"{cat['id']}.json"
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(category_json, f, indent=2, ensure_ascii=False)

        print(f"  Created category: {cat['id']}.json")


def generate_entries(parsed_files: List[Dict], category_config: List[Dict], cross_ref_map: Dict, output_dir: Path):
    """Generate entry JSON files."""
    output_dir.mkdir(parents=True, exist_ok=True)

    # Build filename to category mapping
    file_to_category = {cat['file']: cat for cat in category_config}

    converter = PatchouliConverter(cross_ref_map)

    total_entries = 0
    total_pages = 0

    for parsed in parsed_files:
        filename = parsed['filename']
        category = file_to_category.get(filename)

        if not category:
            continue

        category_id = category['id']
        category_dir = output_dir / category_id
        category_dir.mkdir(parents=True, exist_ok=True)

        print(f"\n  Category: {category['name']}")

        for idx, section in enumerate(parsed['h2_sections']):
            entry_id = converter.to_snake_case(section['title'])
            entry_name = section['title']

            # Generate pages
            pages = converter.split_into_pages(section['content'], entry_name)

            if not pages:
                continue

            entry_json = {
                "name": entry_name,
                "icon": category['icon'],
                "category": f"fpscompress:{category_id}",
                "sortnum": idx,
                "pages": pages
            }

            output_file = category_dir / f"{entry_id}.json"
            with open(output_file, 'w', encoding='utf-8') as f:
                json.dump(entry_json, f, indent=2, ensure_ascii=False)

            print(f"    Entry {idx}: {entry_id}.json ({len(pages)} pages)")
            total_entries += 1
            total_pages += len(pages)

    return total_entries, total_pages


def validate_json_files(categories_dir: Path, entries_dir: Path):
    """Validate all generated JSON files."""
    errors = []

    # Validate categories
    for json_file in categories_dir.glob("*.json"):
        try:
            with open(json_file, 'r', encoding='utf-8') as f:
                data = json.load(f)
                required = ['name', 'description', 'icon', 'sortnum']
                for field in required:
                    if field not in data:
                        errors.append(f"{json_file.name}: Missing field '{field}'")
        except json.JSONDecodeError as e:
            errors.append(f"{json_file.name}: Invalid JSON - {e}")

    # Validate entries
    for category_dir in entries_dir.iterdir():
        if category_dir.is_dir():
            for json_file in category_dir.glob("*.json"):
                try:
                    with open(json_file, 'r', encoding='utf-8') as f:
                        data = json.load(f)
                        required = ['name', 'icon', 'category', 'sortnum', 'pages']
                        for field in required:
                            if field not in data:
                                errors.append(f"{json_file.name}: Missing field '{field}'")

                        if 'pages' in data and len(data['pages']) == 0:
                            errors.append(f"{json_file.name}: No pages")
                except json.JSONDecodeError as e:
                    errors.append(f"{json_file.name}: Invalid JSON - {e}")

    return errors


def main():
    """Main conversion function."""
    print("="*60)
    print("WIKI to Patchouli Conversion")
    print("="*60)

    # Parse all WIKI files
    print("\n[Phase 1] Parsing WIKI files...")
    parsed_files = []

    for cat_config in CATEGORY_CONFIG:
        wiki_file = WIKI_DIR / cat_config['file']
        if not wiki_file.exists():
            print(f"  WARNING: {cat_config['file']} not found")
            continue

        with open(wiki_file, 'r', encoding='utf-8') as f:
            content = f.read()

        parser = MarkdownParser(content, cat_config['file'])
        parsed = parser.parse()
        parsed_files.append(parsed)
        print(f"  Parsed: {cat_config['file']} ({len(parsed['h2_sections'])} sections)")

    # Build cross-reference map
    print("\n[Phase 2] Building cross-reference map...")
    cross_ref_map = build_cross_reference_map(parsed_files, CATEGORY_CONFIG)
    print(f"  Created {len(cross_ref_map)} cross-reference mappings")

    # Generate categories
    print("\n[Phase 3] Generating categories...")
    generate_categories(CATEGORY_CONFIG, CATEGORIES_DIR)

    # Generate entries
    print("\n[Phase 4] Generating entries...")
    total_entries, total_pages = generate_entries(parsed_files, CATEGORY_CONFIG, cross_ref_map, ENTRIES_DIR)

    # Validate
    print("\n[Phase 5] Validating JSON files...")
    errors = validate_json_files(CATEGORIES_DIR, ENTRIES_DIR)

    # Report
    print("\n" + "="*60)
    print("CONVERSION REPORT")
    print("="*60)
    print(f"Files processed: {len(parsed_files)}")
    print(f"Categories created: {len(CATEGORY_CONFIG)}")
    print(f"Total entries: {total_entries}")
    print(f"Total pages: {total_pages}")
    print(f"Cross-references: {len(cross_ref_map)}")

    if errors:
        print(f"\n[!] Validation errors: {len(errors)}")
        for error in errors:
            print(f"  - {error}")
    else:
        print("\n[OK] All JSON files valid")

    print("\n" + "="*60)
    print("Conversion complete!")
    print("="*60)


if __name__ == "__main__":
    main()
