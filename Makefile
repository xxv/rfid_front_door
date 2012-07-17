.PHONY: housing
all: README.xhtml housing

README.xhtml: README.md .XHTML_HEADER .XHTML_FOOTER
	markdown $< | cat .XHTML_HEADER - .XHTML_FOOTER > $@

housing:
	make -C $@
