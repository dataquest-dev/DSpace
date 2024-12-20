# -*- coding: utf-8 -*-
# !/usr/bin/python

import sys
from oval import validator

if len(sys.argv) < 2:
    print("Please, input oai request url!")
    sys.exit(1)
if not sys.argv[1].startswith("http"):
    print("Invalid url supplied (not starting with http)")
    sys.exit(1)

print("\nFirst argument is " + sys.argv[0])
print("\nSecond argument is " + sys.argv[1])

validator.main({"base_url":"http://localhost:8080/server/oai/"})
