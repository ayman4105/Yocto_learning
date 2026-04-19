SUMMARY="TEST: dash 1.0"
DESCRIPTION="This is a test recipe for dash 1.0"

LICENSE="CLOSED"

SRC_URI="git://github.com/danishprakash/dash.git;branch=master;protocol=https"
SRC_URI:append = " file://myfix.patch"

SRCREV = "a9481f4a453f0ad25d9c9068c7b6e47253532deb"

FILES:${PN} = " /usr/ayman/bin/*"


S = "${WORKDIR}/git"


do_compile() {
    oe_runmake 
}


do_install() {
    install -d ${D}/usr/ayman/bin
    install -m 0755 ${S}/dash ${D}/usr/ayman/bin/dash
}