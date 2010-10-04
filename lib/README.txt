The dependencies are hand-managed in Maven-2-like terminology.
The folder names correspond to Maven scopes (top level) and Maven group-id (nested level).

They are not managed by Maven because DLL hell ensues between unnecessary lily-client dependencies 
existing and pdi/libext libraries.

buildtime/
    Classes that are on the buildtime classpath and that are used at runtime 
    (copied to the PDI libext/bugzilla_etl)

buildtime_provided/
    Classes on the buildtime classpath that are provided at runtime by PDI

runtime/
    Classes that are only needed at runtime 
    (transitive dependencies of the buildtime dependencies).

