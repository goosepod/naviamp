# Connect one-flow acceptance (#713)

Status: **not yet run on physical devices.** Automated shared tests pass (see the PR). Record the
build, devices, and result for each row before merging.

| # | Scenario | Expected | Result |
| --- | --- | --- | --- |
| 1 | Fresh Android TV + phone: open Naviamp on the TV, type the code on the phone, choose **Done** | TV signs in and lands on Home; no input on the TV after launch | |
| 2 | Desktop → TV by code | Desktop connects by typing the code; post-connect sheet appears | |
| 3 | Phone → phone by code | Connects; both list each other under Your devices | |
| 4 | Already-trusted TV + second phone | Second phone connects with the shown code; no approval prompt | |
| 5 | Trusted resume with the code hidden, then with it shown | Phone reconnects both times; a shown code is not consumed | |
| 6 | Wrong code five times | Each failure shows a new code; after five, new codes pause ~30 s with a clear message | |
| 7 | Leave the code on screen past 5 minutes | A new code appears automatically | |
| 8 | Previous release on the phone, this build on the TV | Phone resumes and controls the TV | |
| 9 | Signed-in TV, new phone connects | Sign-in is not preselected; **Done** changes nothing on the TV | |
| 10 | **Play on …** with music playing on the phone | Queue continues on the TV at the current position | |
| 11 | Local-network permission denied on the phone | Recovery panel appears in *Connect a device* instead of an empty list | |
| 12 | Spanish and one RTL language (Arabic) | Devices pages and TV setup are fully translated and laid out correctly | |
