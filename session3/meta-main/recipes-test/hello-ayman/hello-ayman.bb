SUMMARY = "ayman"
DESCRIPTION = "TEST YOCTO RECIPE"
LICENSE = "CLOSED"

SRC_URI = "git://github.com/ayman4105/test_yocto.git;protocol=https;branch=main \
           file://fix.patch"

SRCREV = "3c801282b99e0f40ebd044bb3fd7c2e03b8b09a5"

S = "${WORKDIR}/git"

do_compile() {
    ${CC} ${CFLAGS} ${LDFLAGS} ${S}/main.c -o ayman
}

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${S}/ayman ${D}${bindir}
}