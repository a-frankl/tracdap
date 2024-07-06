# Copyright 2021 Accenture Global Solutions Limited
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

ALLOWED_LICENSES="Apache Software License"
ALLOWED_LICENSES="${ALLOWED_LICENSES};MIT License"
ALLOWED_LICENSES="${ALLOWED_LICENSES};BSD License"
ALLOWED_LICENSES="${ALLOWED_LICENSES};BSD-3-Clause"
ALLOWED_LICENSES="${ALLOWED_LICENSES};3-Clause BSD License;"
ALLOWED_LICENSES="${ALLOWED_LICENSES};Python Software Foundation License"
ALLOWED_LICENSES="${ALLOWED_LICENSES};ISC License (ISCL)"
ALLOWED_LICENSES="${ALLOWED_LICENSES};The Unlicense (Unlicense)"

# The "certifi" package is a dependency of Python Safety, licensed under MPL 2.0
# It is OK to use since the compliance tools are not distributed
# So, we can exclude the package from our license report

IGNORE_LICENSE=certifi
