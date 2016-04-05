package org.jnode.rest.fido;

import jnode.dto.Echoarea;
import jnode.dto.Link;

import javax.inject.Named;

@Named("mock-ftnToolsProxy")
public class FtnToolsProxyMock implements FtnToolsProxy{
    @Override
    public Echoarea getAreaByName(String name, Link link) {
        return new Echoarea();
    }

    @Override
    public Long writeEchomail(Echoarea area, String subject, String text, String fromName, String toName) {
        return 1L;
    }

    @Override
    public String defaultEchoFromName() {
        return "me";
    }

    @Override
    public String defaultEchoToName() {
        return "All";
    }
}
