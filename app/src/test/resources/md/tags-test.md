# Markdown Tag Coverage Test

Text before the table. Below is a three-column table with a header.

| Tag | Status | Note |
| --- | --- | --- |
| emphasis | italic | most common |
| empty-line | gap | blank line |
| table | grid | this section |

Text after the table. Below is a table without outer pipes:

Name | Age
--- | ---
Alice | 30
Bob | 25

Below is a table with mixed content (#10): a narrow “No.” column next to a
long description. The “No.” column must be narrow, the description gets the rest.

| No. | Tag | Description |
| --- | --- | --- |
| 1 | poem | A poem: lines do not wrap, the block is as wide as its longest line, the author on the right. |
| 2 | notes | A footnote opens in the bottom sheet; a tap on the marker works on a justified line too. |
| 3 | table | A grid with a border around every cell; column widths depend on the content. |

Below is a table wider than the page: no column may shrink below its
longest word, and the table must not run past the margins.

| Code | Long description | Even longer description |
| --- | --- | --- |
| AB-1 | A very long description that does not fit on the page in any way at all. | A second very long description that does not fit either and has to share the width. |
| CD-2 | Electroencephalography | Thelongestunbreakablewordinthewholetable |

Below is a table with alignment (#14). The first column left, the second
centered, the third right, the fourth says nothing (must stay left).

| Left | Center | Right | Silent |
| :--- | :---: | ----: | ------- |
| a | b | 1 | x |
| a longer line | a longer line | 1234 | a longer line |
| c | d | 22 | y |

The “Right” column is numeric: the digits must line up on the right edge one
under another. Column widths must be the same as they would be without colons.

## Inline markdown

A markdown file is markdown: **bold**, _italic_, `code`, [a link](https://example.org/md), and an author's literal (*) that must stay.

End of test.
