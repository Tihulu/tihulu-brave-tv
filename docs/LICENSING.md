# Licensing

## Repository code

Unless a file states otherwise, original Tihulu TV Browser source code and tooling in this repository are licensed under **GNU Affero General Public License v3.0 only (AGPL-3.0-only)**.

## Brave and Chromium are not relicensed

This project is an overlay for upstream software. The repository's AGPL license does not replace the licenses of software fetched during bootstrap.

- Brave Core is primarily licensed under the Mozilla Public License 2.0 (MPL-2.0).
- Chromium source is largely BSD-style licensed and includes many third-party components under additional compatible licenses.
- Other Brave/Chromium dependencies retain their own copyright and license notices.

When `scripts/apply_overlay.py` modifies an existing upstream file, the resulting upstream file continues to carry its upstream copyright/license header and must be distributed according to the applicable upstream terms.

## MPL and AGPL boundary

MPL 2.0 is file-level copyleft and its definition of a permitted “Secondary License” includes GNU AGPL v3.0. This makes it possible to combine independently written AGPL code with MPL-covered Brave code under the conditions of the licenses, while preserving MPL notices and source obligations for MPL files.

That does not mean every Chromium dependency can be called AGPL. Distribution must satisfy the license of every included component.

## Practical distribution rule

For an APK or source bundle:

1. Keep upstream copyright/license notices.
2. Provide the source/license obligations required by Brave/Chromium and their bundled components.
3. Provide this project's AGPL-3.0 source for the TV layer.
4. Do not delete Brave/Chromium third-party notices.
5. Keep independent product branding unless you have trademark permission.

This file documents the intended project licensing structure; it is not legal advice.
