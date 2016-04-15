package org.jnode.rest.handler.secure;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.util.NamedParamsRetriever;
import jnode.dto.Echoarea;
import org.jnode.rest.core.RPCError;
import org.jnode.rest.db.RestUser;
import org.jnode.rest.di.Inject;
import org.jnode.rest.di.Named;
import org.jnode.rest.fido.FtnToolsProxy;
import org.jnode.rest.handler.AbstractHandler;

public class EchomailPostHandler  extends AbstractHandler {

    @Inject
    @Named("ftnToolsProxy")
    private FtnToolsProxy ftnToolsProxy;

    @Override
    public String[] handledRequests() {
        return new String[]{"echomail.post"};
    }

    @Override
    protected JSONRPC2Response createJsonrpc2Response(Object reqID, NamedParamsRetriever np) throws JSONRPC2Error {
        Echoarea echoarea = ftnToolsProxy.getAreaByName(np.getString("echoarea"), null);
        if (echoarea == null) {
            return new JSONRPC2Response(RPCError.ECHOAREA_NOT_FOUND, reqID);
        }

        Long id = ftnToolsProxy.writeEchomail(echoarea, np.getString("subject"),
                np.getString("body"),
                np.getOptString("fromName", ftnToolsProxy.defaultEchoFromName()),
                np.getOptString("toName", ftnToolsProxy.defaultEchoToName()),
                np.getOptString("fromFTN", ftnToolsProxy.defaultFromFtn()),
                np.getOptString("tearline", ftnToolsProxy.defaultTearline()),
                np.getOptString("origin", ftnToolsProxy.defaultOrigin())
        );

        return new JSONRPC2Response(id, reqID);
    }

    @Override
    protected RestUser.Type[] secured() {
        return new RestUser.Type[]{RestUser.Type.USER, RestUser.Type.ADMIN};
    }

    public void setFtnToolsProxy(FtnToolsProxy ftnToolsProxy) {
        this.ftnToolsProxy = ftnToolsProxy;
    }


}
