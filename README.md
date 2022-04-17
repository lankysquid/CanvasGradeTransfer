# Simple Grade Transfer Program
This project uses the [KSU Canvas LMS Library](https://github.com/kstateome/canvas-api) to access the grades in project stem's canvas instance and transfer it to our school instance.

Normally I would simply add the library in Maven, but project stem has so many assignments that the assignment ID's could not be represented as an `int`, so I needed to change the library to use `long` for the assignment IDs.

`TransferStudentGrades` requires updated tokens to operate properly.


