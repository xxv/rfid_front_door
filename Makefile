README.xhtml: README.md
	markdown $< | cat .XHTML_HEADER - .XHTML_FOOTER > $@
