
index.php: pegasus-book.xml pegasus-style.xsl
	rm -rf html
	mkdir -p html
	xsltproc --noout \
		      --stringparam base.dir html/ \
		      --xinclude \
	         pegasus-style.xsl \
	         pegasus-book.xml
	cp -r images html/

clean:
	  rm -rf html ./*~

