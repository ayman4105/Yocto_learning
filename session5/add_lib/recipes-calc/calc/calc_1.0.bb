SUMMARY = "A simple calculator"
DESCRIPTION = "This is a simple calculator that provides basic arithmetic operations."
LICENSE = "CLOSED"

SRC_URI = "file://main.c"
S = "${WORKDIR}"
DEPENDS = "ayman"

do_compile() {
    ${CC} ${CFLAGS} ${LDFLAGS} ${S}/main.c -L${STAGING_LIBDIR} -I${STAGING_INCDIR} -lmath -o ${S}/calc
}


do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${S}/calc ${D}${bindir}/calc
}

#FILES:${PN} = "${bindir}/calc"