"""Starter skills vocabulary: canonical name | aliases.

This is a small hand-made list, not the full ESCO classification. Extend it with the skills
that appear in collected Kenyan listings (see the proposal, Section 3.10).
"""

_RAW = """
Python | python3
Java | java
JavaScript | javascript, js, ecmascript
TypeScript | typescript
Kotlin | kotlin
Swift | swift
C++ | c++, cpp
C# | c#, csharp
C programming | c programming
PHP | php
Ruby | ruby
Go | golang
Rust | rust
R | r programming, r language
SQL | sql, structured query language
MATLAB | matlab
Bash | bash, shell scripting
Dart | dart
Scala | scala
Assembly | assembly language, x86 assembly, nasm, 8085
HTML | html, html5
CSS | css, css3
React | react, reactjs, react.js
Angular | angular
Vue | vue, vuejs
Node.js | node.js, nodejs, node js
Django | django
Flask | flask
FastAPI | fastapi
Spring Boot | spring boot
Laravel | laravel
REST APIs | rest api, rest apis, restful, restful apis
GraphQL | graphql
Android development | android, android development, android studio
Flutter | flutter
iOS development | ios, swiftui
Jetpack Compose | jetpack compose
React Native | react native
Web development | web development, web design
Data analysis | data analysis, data analytics, analytics
Data visualisation | data visualization, data visualisation, dashboards
Power BI | power bi, powerbi
Tableau | tableau
Excel | excel, microsoft excel, spreadsheets, pivot tables
Pandas | pandas
NumPy | numpy
Statistics | statistics, statistical analysis
ETL | etl, data pipelines
Data cleaning | data cleaning, data cleansing
Machine learning | machine learning, ml
Deep learning | deep learning, neural networks
NLP | nlp, natural language processing
TensorFlow | tensorflow
PyTorch | pytorch
scikit-learn | scikit-learn, sklearn
Data mining | data mining
Big data | big data, hadoop
Apache Spark | apache spark, pyspark
MySQL | mysql
PostgreSQL | postgresql, postgres
MongoDB | mongodb
Oracle | oracle database, oracle
SQL Server | sql server, mssql
SQLite | sqlite
Database design | database design, data modelling, data modeling, erd
NoSQL | nosql
Firebase | firebase
Git | git, github, gitlab, version control
Docker | docker, containers
Kubernetes | kubernetes, k8s
AWS | aws, amazon web services
Azure | azure, microsoft azure
Google Cloud | gcp, google cloud
Linux | linux, ubuntu
CI/CD | ci/cd, continuous integration
DevOps | devops
Networking | networking, tcp/ip, routing and switching
Cisco | cisco, ccna
Windows Server | windows server, active directory
Server administration | server administration, system administration, sysadmin
Virtualisation | virtualization, virtualisation, vmware, virtualbox
Cybersecurity | cybersecurity, cyber security, information security, infosec
Penetration testing | penetration testing, pentesting, ethical hacking
Cryptography | cryptography, encryption
Software testing | software testing, quality assurance, unit testing, test cases
Agile | agile, scrum, kanban
Object-oriented programming | oop, object-oriented, object oriented
Data structures and algorithms | data structures, algorithms
System design | system design, software architecture
Requirements gathering | requirements gathering, requirements analysis, business requirements
UML | uml
Technical documentation | technical documentation, documentation
Debugging | debugging, troubleshooting
Embedded systems | embedded systems, microcontrollers, arduino, raspberry pi
IoT | iot, internet of things
Computer graphics | computer graphics, opengl
UX design | ux, user experience, usability, hci
UI design | ui, user interface design
Figma | figma
Wireframing | wireframing, prototyping, wireframes
Graphic design | graphic design, photoshop, illustrator, canva
Video editing | video editing, premiere
Digital marketing | digital marketing, seo, social media marketing
Content creation | content creation, copywriting, content writing
Social media management | social media management, social media
Accounting | accounting, bookkeeping, book keeping
Financial reporting | financial reporting, financial statements
Auditing | auditing, audit
Tax | tax, taxation
QuickBooks | quickbooks
Financial analysis | financial analysis, financial modelling, financial modeling
Budgeting | budgeting, forecasting
Project management | project management, project coordination
Business analysis | business analysis, business analyst
Customer service | customer service, customer support, client service
Sales | sales, business development
Market research | market research
Procurement | procurement, supply chain, logistics
Human resources | human resources, hr, recruitment
Operations research | operations research, linear programming
Research | research skills, research methods, literature review
Legal research | legal research, legal drafting, contract drafting
Data entry | data entry
Administration | administrative, office administration
Event planning | event planning, event management
Communication | communication, communication skills, verbal communication, written communication
Teamwork | teamwork, team player, collaboration, work in a team, work as part of a team
Leadership | leadership, lead a team
Problem solving | problem solving, problem-solving, analytical thinking, analytical skills
Time management | time management, meet deadlines, organisational skills, organizational skills
Presentation | presentation skills, presentations, public speaking
Adaptability | adaptability, fast learner, learn quickly, willingness to learn
Attention to detail | attention to detail, detail-oriented, detail oriented
Critical thinking | critical thinking
Creativity | creativity, creative thinking
Negotiation | negotiation
Interpersonal skills | interpersonal skills
Microsoft Office | microsoft office, ms office, office suite
Report writing | report writing
French | french
Swahili | kiswahili, swahili
"""

SKILLS: dict[str, list[str]] = {}
for _line in _RAW.strip().splitlines():
    _name, _aliases = (p.strip() for p in _line.split("|", 1))
    SKILLS[_name] = sorted({_name.lower(), *(a.strip().lower() for a in _aliases.split(",") if a.strip())})

# Skills close enough that holding one earns partial credit for another (never full credit).
FAMILIES: list[set[str]] = [
    {"SQL", "MySQL", "PostgreSQL", "SQL Server", "SQLite", "Oracle", "Database design"},
    {"Java", "Kotlin", "Android development", "Jetpack Compose"},
    {"JavaScript", "TypeScript", "Node.js", "React", "Angular", "Vue", "React Native"},
    {"Python", "Pandas", "NumPy", "scikit-learn", "Django", "Flask", "FastAPI"},
    {"Machine learning", "Deep learning", "TensorFlow", "PyTorch", "scikit-learn", "NLP", "Data mining"},
    {"Power BI", "Tableau", "Data visualisation", "Excel", "Data analysis"},
    {"AWS", "Azure", "Google Cloud", "Docker", "Kubernetes", "DevOps", "CI/CD"},
    {"Networking", "Cisco", "Server administration", "Windows Server", "Linux", "Virtualisation"},
    {"Cybersecurity", "Penetration testing", "Cryptography"},
    {"UX design", "UI design", "Figma", "Wireframing", "Graphic design"},
    {"Accounting", "Financial reporting", "Auditing", "Tax", "QuickBooks", "Financial analysis", "Budgeting"},
    {"Digital marketing", "Social media management", "Content creation", "Market research"},
    {"Android development", "Flutter", "iOS development", "React Native", "Jetpack Compose"},
]
