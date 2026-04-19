SUMMARY = "A simple math library"
DESCRIPTION = "This is a simple math library that provides basic arithmetic operations."
LICENSE = "CLOSED"



SRC_URI = "file://mymath.c file://mymath.h"
S = "${WORKDIR}"

PROVIDES="ayman"

do_compile() {
    ${CC} ${CFLAGS} -c ${S}/mymath.c -o ${S}/mymath.o
    ${AR} rcs ${S}/libmath.a ${S}/mymath.o
}

do_install() {
    install -d ${D}${libdir}
    install -d ${D}${includedir}/

    install -m 0644 ${S}/libmath.a ${D}${libdir}/libmath.a
    install -m 0644 ${S}/mymath.h ${D}${includedir}/mymath.h
}


FILES:${PN}-dev = "${includedir}/* ${libdir}/*"
FILES:{PN} = "${includedir}/* ${libdir}/*"
