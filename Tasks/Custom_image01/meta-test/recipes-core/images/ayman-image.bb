SUMMARY = "Ayman Image"
DESCRIPTION = "This is a simple image processing library that provides basic image manipulation operations."

LICENSE = "CLOSED"


# This image includes the core boot packages and any additional packages specified in CORE_IMAGE_EXTRA_INSTALL.
IMAGE_INSTALL = "packagegroup-core-boot ${CORE_IMAGE_EXTRA_INSTALL}"


# The following line is used to specify additional packages to be included in the image. You can add any packages you want here.
inherit core-image


IMAGE_FEATURES += "debug-tweaks ssh-server-dropbear"

IMAGE_INSTALL:append = " packagegroup-core-boot vim python3 calc tcpdump"

IMAGE_INSTALL:append = " hello-ayman"
IMAGE_INSTALL:append = " dash"
IMAGE_INSTALL:append = " math-dev"