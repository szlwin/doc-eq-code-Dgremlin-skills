"""Bounded XML input with DTD/entity declarations rejected before DOM creation.

This small stdlib-only module is also shipped in the independently usable
implementation skill. Keep the two copies identical (covered by tests).
"""
from __future__ import annotations

from pathlib import Path
from xml.dom import minidom
from xml.parsers import expat

MAX_XML_BYTES = 8 * 1024 * 1024


def read_safe_xml_bytes(path: Path) -> bytes:
    """Read bounded XML and reject declarations before any tree parser runs."""
    with Path(path).open("rb") as stream:
        content = stream.read(MAX_XML_BYTES + 1)
    _validate_xml(content, str(path))
    return content


def _validate_xml(content: str | bytes, source: str) -> None:
    size = len(content.encode("utf-8")) if isinstance(content, str) else len(content)
    if size > MAX_XML_BYTES:
        raise ValueError(f"XML exceeds {MAX_XML_BYTES} bytes: {source}")
    # Expat handles encoding (including UTF-16); a text/byte substring check
    # would either miss declarations or reject harmless comments and CDATA.
    parser = expat.ParserCreate()

    def reject(*_):
        raise ValueError(f"DTD and ENTITY declarations are forbidden: {source}")

    parser.StartDoctypeDeclHandler = reject
    parser.EntityDeclHandler = reject
    parser.ExternalEntityRefHandler = reject
    parser.Parse(content, True)


def parse_safe_xml(content: str | bytes) -> minidom.Document:
    """Apply the same declaration and size checks to in-memory XML."""
    # A str is already decoded. Re-encoding it while retaining a different XML
    # encoding declaration would corrupt non-ASCII text or reject valid input.
    _validate_xml(content, "<memory>")
    return minidom.parseString(content)


def load_safe_xml(path: Path) -> minidom.Document:
    return minidom.parseString(read_safe_xml_bytes(path))
