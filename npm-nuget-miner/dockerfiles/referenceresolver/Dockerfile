FROM python:3.8.2-alpine3.11	

ENV PYTHONUNBUFFERED=1

RUN pip install neo4j

COPY ./dependencygraphutils/ /usr/app/reference_resolver/dependencygraphutils
COPY ./referenceresolving/ /usr/app/reference_resolver/referenceresolving
COPY ./referenceentry.py /usr/app/reference_resolver/referenceentry.py

WORKDIR /usr/app/reference_resolver

ENTRYPOINT python /usr/app/reference_resolver/referenceentry.py
